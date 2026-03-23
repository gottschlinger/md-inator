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
import java.util.*;

/**
 * Split-output processor — groups matched files by their leaf directory and
 * writes one {@code .md} file per group into a flat output directory.
 *
 * <h2>Leaf directory definition</h2>
 * A directory is a <em>leaf</em> if it directly contains at least one matched
 * source file.  Parent directories that only contain subdirectories are skipped.
 *
 * <h2>Output file naming</h2>
 * Each output file is named after its leaf directory using a dot-separated
 * package-style path relative to the repo root, e.g.:
 * <pre>
 *   src/main/java/com/foo  →  src.main.java.com.foo.md
 * </pre>
 * If two different repos happen to produce the same name (unlikely in practice)
 * a numeric suffix is appended to avoid overwriting.
 */
public final class SplitProcessor {

    private final ProcessingConfig config;

    public SplitProcessor(ProcessingConfig config) {
        this.config = config;
    }

    public ProcessingResult process() throws IOException {
        // 1. Collect all matched files
        FileCollector collector = new FileCollector(config);
        List<SourceFile> allFiles = collector.collect();

        if (allFiles.isEmpty()) {
            throw new IOException(
                "No files matched the given include patterns. "
                + "Check your --include globs and repo path."
            );
        }

        // 2. Group by leaf directory (the parent dir of each file's relative path)
        Map<String, List<SourceFile>> groups = groupByLeafDirectory(allFiles);

        // 3. Ensure output directory exists
        Path outDir = config.getOutputDir();
        Files.createDirectories(outDir);

        // 4. Write one .md per group
        ProcessingResult.Builder result = ProcessingResult.builder()
            .includedFileCount(allFiles.size())
            .excludedFileCount(collector.getExcludedCount());

        long totalSize   = 0;
        long totalTokens = 0;
        Set<String> usedNames = new HashSet<>();

        // Sort groups so output is deterministic
        List<Map.Entry<String, List<SourceFile>>> sortedGroups =
            new ArrayList<>(groups.entrySet());
        sortedGroups.sort(Map.Entry.comparingByKey());

        for (Map.Entry<String, List<SourceFile>> entry : sortedGroups) {
            String dirKey        = entry.getKey();  // forward-slash relative dir
            List<SourceFile> files = entry.getValue();

            // Derive output filename from the directory path
            String baseName  = toFileName(dirKey);
            String fileName  = deduplicateName(baseName, usedNames);
            usedNames.add(fileName);

            Path outPath = outDir.resolve(fileName);

            // Build tree scoped to just this group's files
            String leafLabel = leafLabel(dirKey);
            String tree = config.isIncludeTree()
                ? TreeBuilder.build(files, leafLabel)
                : "";

            MarkdownWriter writer = new MarkdownWriter(config, dirKey);
            try (FileWriter fw = new FileWriter(outPath.toFile(), StandardCharsets.UTF_8)) {
                writer.write(fw, files, tree);
            }

            long size   = Files.size(outPath);
            long tokens = TokenEstimator.estimateFromBytes(size);
            totalSize   += size;
            totalTokens += tokens;

            result.addOutputFile(new ProcessingResult.OutputFile(
                outPath, leafLabel, files.size(), size, tokens));

            if (config.isVerbose()) {
                System.err.printf("[split] wrote %s (%d files, ~%,d tokens)%n",
                    fileName, files.size(), tokens);
            }
        }

        result.totalOutputSizeBytes(totalSize)
              .totalEstimatedTokens(totalTokens);

        // 5. Warnings
        if (totalTokens > (long) config.getMaxTokens() * groups.size()) {
            result.addWarning(String.format(
                "Total estimated tokens (~%,d across %d files) is very large.",
                totalTokens, groups.size()));
        }

        // Warn on any individual file that exceeds the budget
        for (ProcessingResult.OutputFile of : result.build().getOutputFiles()) {
            if (of.getEstimatedTokens() > config.getMaxTokens()) {
                result.addWarning(String.format(
                    "%s: ~%,d tokens exceeds --max-tokens (%,d). Consider narrowing patterns.",
                    of.getPath().getFileName(), of.getEstimatedTokens(), config.getMaxTokens()));
            }
        }

        return result.build();
    }

    // -------------------------------------------------------------------------

    /**
     * Group files by their immediate parent directory (relative to repo root).
     * Files at the repo root are grouped under the key {@code ""} (empty string).
     */
    private static Map<String, List<SourceFile>> groupByLeafDirectory(List<SourceFile> files) {
        Map<String, List<SourceFile>> groups = new LinkedHashMap<>();
        for (SourceFile f : files) {
            String rel    = f.getRelativePathString();
            int lastSlash = rel.lastIndexOf('/');
            String dir    = lastSlash >= 0 ? rel.substring(0, lastSlash) : "";
            groups.computeIfAbsent(dir, k -> new ArrayList<>()).add(f);
        }
        return groups;
    }

    /**
     * Convert a forward-slash directory path to a dot-separated filename.
     *
     * <pre>
     *   src/main/java/com/example/mod/network  →  src.main.java.com.example.mod.network.md
     *   ""  (repo root files)                  →  _root.md
     * </pre>
     */
    private static String toFileName(String dirKey) {
        if (dirKey.isEmpty()) return "_root.md";
        return dirKey.replace('/', '.') + ".md";
    }

    /**
     * If {@code baseName} is already used, append a numeric suffix until unique.
     */
    private static String deduplicateName(String baseName, Set<String> used) {
        if (!used.contains(baseName)) return baseName;
        String stem = baseName.endsWith(".md")
            ? baseName.substring(0, baseName.length() - 3)
            : baseName;
        int i = 2;
        String candidate;
        do { candidate = stem + "-" + i++ + ".md"; } while (used.contains(candidate));
        return candidate;
    }

    /**
     * Derive a human-readable label from the directory key for use in the
     * .md header and tree.  Returns the last path segment, or the repo name
     * for root-level files.
     */
    private String leafLabel(String dirKey) {
        if (dirKey.isEmpty()) {
            return config.getRepoPath().getFileName() != null
                ? config.getRepoPath().getFileName().toString()
                : "repo";
        }
        int last = dirKey.lastIndexOf('/');
        return last >= 0 ? dirKey.substring(last + 1) : dirKey;
    }
}
