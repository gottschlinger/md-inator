package gottsch.mdinator.github;

import gottsch.mdinator.core.RepoProcessor;
import gottsch.mdinator.core.TreeBuilder;
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
 * Orchestrates a remote GitHub repo fetch into a single Markdown output file.
 * Mirrors {@link RepoProcessor} but uses
 * {@link GitHubFileCollector} instead of the local filesystem walker.
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class GitHubRepoProcessor {

    private final GitHubSource source;
    private final ProcessingConfig config;
    private final GitHubApiClient client;

    public GitHubRepoProcessor(GitHubSource source, ProcessingConfig config,
                               GitHubApiClient client) {
        this.source = source;
        this.config = config;
        this.client = client;
    }

    public ProcessingResult process() throws IOException {
        // 1. Collect files from GitHub
        GitHubFileCollector collector = new GitHubFileCollector(source, config, client);
        List<SourceFile> files = collector.collect();

        if (files.isEmpty()) {
            throw new IOException(
                    "No files matched the given include patterns for " + source.getLabel()
                            + ". Check your --include globs, repo name, and branch.");
        }

        // 2. Build optional ASCII tree
        String tree = config.isIncludeTree()
                ? TreeBuilder.build(files, source.getRepo())
                : "";

        // 3. Write output — use repo name as the label in the header
        Path outputPath = config.getOutputPath();
        Files.createDirectories(outputPath.getParent() != null
                ? outputPath.getParent() : Path.of("."));

        // Pass the GitHub label as dirContext so the header shows owner/repo[@branch]
        MarkdownWriter writer = new MarkdownWriter(config, source.getLabel());
        try (FileWriter fw = new FileWriter(outputPath.toFile(), StandardCharsets.UTF_8)) {
            writer.write(fw, files, tree);
        }

        // 4. Metrics
        long outputSize    = Files.size(outputPath);
        long tokenEstimate = TokenEstimator.estimateFromBytes(outputSize);

        ProcessingResult.Builder result = ProcessingResult.builder()
                .addOutputFile(new ProcessingResult.OutputFile(
                        outputPath, source.getLabel(),
                        files.size(), outputSize, tokenEstimate))
                .includedFileCount(files.size())
                .excludedFileCount(collector.getExcludedCount())
                .totalOutputSizeBytes(outputSize)
                .totalEstimatedTokens(tokenEstimate);

        // 5. Warnings
        if (tokenEstimate > config.getMaxTokens()) {
            result.addWarning(String.format(
                    "Estimated token count (~%,d) exceeds --max-tokens limit (%,d).",
                    tokenEstimate, config.getMaxTokens()));
        }

        return result.build();
    }
}