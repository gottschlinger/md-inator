package gottsch.mdinator.core;

import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.ProcessingResult;
import gottsch.mdinator.model.SourceFile;
import gottsch.mdinator.output.MarkdownWriter;
import gottsch.mdinator.util.TokenEstimator;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Auto-chunking processor — splits matched files across multiple {@code .md}
 * output files so that each chunk stays within a given token budget.
 *
 * <h2>Strategy</h2>
 * Files are processed in alphabetical order (same as single-file mode).
 * Each file's token cost is estimated before adding it to the current chunk.
 * When adding the next file would exceed the budget, the current chunk is
 * written and a new one is started.  A file that is larger than the entire
 * budget on its own is placed in a chunk by itself with a warning.
 *
 * <h2>Output naming</h2>
 * Files are written to the same directory as the normal {@code --output} path,
 * using the base name with a numeric suffix:
 * <pre>
 *   myrepo-context-1.md
 *   myrepo-context-2.md
 *   myrepo-context-3.md
 * </pre>
 *
 * <h2>Chunk header</h2>
 * Each file's header shows {@code Part N of M} so the reader knows where
 * they are in the full set.
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class ChunkProcessor {

    // Header + TOC + tree overhead per chunk (in tokens)
    private static final long CHUNK_OVERHEAD_TOKENS = 1_000L;

    private final ProcessingConfig config;
    private final long chunkTokenBudget;

    public ChunkProcessor(ProcessingConfig config, long chunkTokenBudget) {
        this.config           = config;
        this.chunkTokenBudget = chunkTokenBudget;
    }

    public ProcessingResult process() throws IOException {
        // 1. Collect all matched files
        FileCollector collector = new FileCollector(config);
        List<SourceFile> allFiles = collector.collect();

        if (allFiles.isEmpty()) {
            throw new IOException(
                    "No files matched the given include patterns. "
                            + "Check your --include globs and repo path.");
        }

        // 2. Partition into chunks
        List<List<SourceFile>> chunks = partition(allFiles);
        int totalChunks = chunks.size();

        if (config.isVerbose()) {
            System.err.printf("[chunk] %d file(s) → %d chunk(s) at ~%,d tokens/chunk budget%n",
                    allFiles.size(), totalChunks, chunkTokenBudget);
        }

        // 3. Resolve output paths
        Path baseOutput = config.getOutputPath();
        Path outputDir  = baseOutput.getParent() != null
                ? baseOutput.getParent() : Path.of(".");
        String baseName = baseOutputBaseName(baseOutput);
        Files.createDirectories(outputDir);

        // 4. Write each chunk
        ProcessingResult.Builder result = ProcessingResult.builder()
                .includedFileCount(allFiles.size())
                .excludedFileCount(collector.getExcludedCount());

        long totalSize   = 0;
        long totalTokens = 0;

        for (int i = 0; i < totalChunks; i++) {
            List<SourceFile> chunkFiles = chunks.get(i);
            int chunkNum = i + 1;

            String chunkLabel = "Part " + chunkNum + " of " + totalChunks;
            Path outPath = outputDir.resolve(baseName + "-" + chunkNum + ".md");

            String repoName = config.getRepoPath().getFileName() != null
                    ? config.getRepoPath().getFileName().toString() : "repo";
            String tree = config.isIncludeTree()
                    ? TreeBuilder.build(chunkFiles, repoName)
                    : "";

            MarkdownWriter writer = new MarkdownWriter(config, chunkLabel);
            try (FileWriter fw = new FileWriter(outPath.toFile(), StandardCharsets.UTF_8)) {
                writer.write(fw, chunkFiles, tree);
            }

            long size   = Files.size(outPath);
            long tokens = TokenEstimator.estimateFromBytes(size);
            totalSize   += size;
            totalTokens += tokens;

            result.addOutputFile(new ProcessingResult.OutputFile(
                    outPath, chunkLabel, chunkFiles.size(), size, tokens));

            if (config.isVerbose()) {
                System.err.printf("[chunk] wrote %s (%d files, ~%,d tokens)%n",
                        outPath.getFileName(), chunkFiles.size(), tokens);
            }
        }

        result.totalOutputSizeBytes(totalSize)
                .totalEstimatedTokens(totalTokens);

        return result.build();
    }

    // -------------------------------------------------------------------------

    /**
     * Greedily partition files into chunks, each within the token budget.
     * A file larger than the entire budget is placed in its own chunk with
     * a warning printed to stderr.
     */
    private List<List<SourceFile>> partition(List<SourceFile> files) {
        List<List<SourceFile>> chunks  = new ArrayList<>();
        List<SourceFile>       current = new ArrayList<>();
        long                   running = CHUNK_OVERHEAD_TOKENS;

        for (SourceFile f : files) {
            // Use rendered estimate instead of raw source estimate
            long fileTokens = MarkdownWriter.estimateRenderedTokens(f);

            if (fileTokens > chunkTokenBudget) {
                System.err.printf(
                        "[WARN] %s (~%,d tokens) exceeds chunk budget (%,d). "
                                + "Placing in its own chunk.%n",
                        f.getRelativePathString(), fileTokens, chunkTokenBudget);

                if (!current.isEmpty()) {
                    chunks.add(current);
                    current = new ArrayList<>();
                    running = CHUNK_OVERHEAD_TOKENS;
                }
                chunks.add(List.of(f));
                continue;
            }

            if (running + fileTokens > chunkTokenBudget && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                running = CHUNK_OVERHEAD_TOKENS;
            }

            current.add(f);
            running += fileTokens;
        }

        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    /**
     * Derives the base output name without extension.
     * e.g. {@code /path/to/myrepo-context.md} → {@code myrepo-context}
     */
    private static String baseOutputBaseName(Path outputPath) {
        String name = outputPath.getFileName().toString();
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }
}