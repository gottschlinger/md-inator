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
import java.util.List;

/**
 * Single-file output orchestrator: collect → tree → write → report.
 * For split mode, see {@link SplitProcessor}.
 */
public final class RepoProcessor {

    private final ProcessingConfig config;

    public RepoProcessor(ProcessingConfig config) {
        this.config = config;
    }

    public ProcessingResult process() throws IOException {
        // 1. Collect matching files
        FileCollector collector = new FileCollector(config);
        List<SourceFile> files  = collector.collect();

        if (files.isEmpty()) {
            throw new IOException(
                "No files matched the given include patterns. "
                + "Check your --include globs and repo path."
            );
        }

        // 2. Build optional ASCII tree
        String repoName = config.getRepoPath().getFileName() != null
            ? config.getRepoPath().getFileName().toString()
            : "repo";
        String tree = config.isIncludeTree() ? TreeBuilder.build(files, repoName) : "";

        // 3. Write output
        Path outputPath = config.getOutputPath();
        Files.createDirectories(outputPath.getParent() != null
            ? outputPath.getParent() : Path.of("."));

        MarkdownWriter writer = new MarkdownWriter(config);
        try (FileWriter fw = new FileWriter(outputPath.toFile(), StandardCharsets.UTF_8)) {
            writer.write(fw, files, tree);
        }

        // 4. Compute result metrics
        long outputSize    = Files.size(outputPath);
        long tokenEstimate = TokenEstimator.estimateFromBytes(outputSize);

        ProcessingResult.Builder result = ProcessingResult.builder()
            .addOutputFile(new ProcessingResult.OutputFile(
                outputPath, outputPath.getFileName().toString(),
                files.size(), outputSize, tokenEstimate))
            .includedFileCount(files.size())
            .excludedFileCount(collector.getExcludedCount())
            .totalOutputSizeBytes(outputSize)
            .totalEstimatedTokens(tokenEstimate);

        // 5. Warnings
        if (tokenEstimate > config.getMaxTokens()) {
            result.addWarning(String.format(
                "Estimated token count (~%,d) exceeds --max-tokens limit (%,d). "
                + "Consider narrowing your include patterns or using --split.",
                tokenEstimate, config.getMaxTokens()
            ));
        }

        if (files.size() > 500) {
            result.addWarning(files.size()
                + " files included — consider using --split for large repos.");
        }

        return result.build();
    }
}
