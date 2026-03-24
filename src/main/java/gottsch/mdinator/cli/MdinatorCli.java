package gottsch.mdinator.cli;

import gottsch.mdinator.core.ChunkProcessor;
import gottsch.mdinator.core.FileCollector;
import gottsch.mdinator.core.RepoProcessor;
import gottsch.mdinator.core.SplitProcessor;
import gottsch.mdinator.github.GitHubApiClient;
import gottsch.mdinator.github.GitHubFileCollector;
import gottsch.mdinator.github.GitHubRepoProcessor;
import gottsch.mdinator.github.GitHubSource;
import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.ProcessingResult;
import gottsch.mdinator.model.SourceFile;
import gottsch.mdinator.util.TokenEstimator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Main CLI entry point for md-inator.
 *
 * @author Mark Gottschling on March 21, 2026
 */
@Command(
        name = "md-inator",
        mixinStandardHelpOptions = true,
        versionProvider = MdinatorCli.ManifestVersionProvider.class,
        description = {
                "",
                "  Converts a local or remote (public/private) Git repository into Markdown file(s)",
                "  optimised for ingestion by Claude and other LLMs.",
                ""
        },
        footer = {
                "",
                "Examples:",
                "  # Local repo — these two are equivalent due to implicit **/ prefix:",
                "  md-inator /path/to/repo --include '**/*.java'",
                "  md-inator /path/to/repo --include '*.java'",
                "",
                "  # Remote GitHub repo (full URL)",
                "  md-inator https://github.com/owner/repo --include '**/*.java'",
                "",
                "  # Remote GitHub repo (shorthand)",
                "  md-inator owner/repo --include '**/*.java'",
                "",
                "  # Remote with specific branch",
                "  md-inator owner/repo --include '**/*.java' --branch develop",
                "",
                "  # Split mode — one .md per leaf directory",
                "  md-inator /path/to/repo --include '**/*.java' --split",
                "",
                "  # Private repo with PAT token",
                "  md-inator owner/private-repo --include '**/*.java' --token ghp_yourtoken",
                "",
                "  # Dry-run to confirm what would be matched",
                "  md-inator /path/to/repo --include '**/*.java' --dry-run",
                "",
                "  # Auto-chunk large repos into multiple .md files",
                "  md-inator /path/to/repo --include '**/*.java' --auto-chunk",
                "  md-inator /path/to/repo --include '**/*.java' --auto-chunk --chunk-tokens 100000",
                "",
                "Note: place a .mdinatorignore file in the repo root to exclude paths",
                "  (same syntax as .gitignore — CLI --exclude flags take priority).",
                ""
        }
)
public class MdinatorCli implements Callable<Integer> {

    @Parameters(
            index = "0",
            description = "Local repo path, GitHub URL (https://github.com/owner/repo), "
                    + "or owner/repo shorthand."
    )
    private String repoArg;

    @Option(
            names = {"-i", "--include"},
            description = {
                    "Glob pattern(s) for files to include. Repeatable.",
                    "Patterns are matched against the file's path relative to the repo root.",
                    "A leading '**/' is added implicitly, so '*.java' and '**/*.java' are equivalent.",
                    "Examples: '**/*.java', 'src/**/*.kt', '*.gradle'"
            },
            required = true
    )
    private List<String> includePatterns = new ArrayList<>();

    @Option(
            names = {"-e", "--exclude"},
            description = {
                    "Glob pattern(s) to exclude. Repeatable. Takes priority over --include.",
                    "Patterns are matched against the file's path relative to the repo root.",
                    "A leading '**/' is added implicitly, so 'test/**' and '**/test/**' are equivalent.",
                    "Examples: '**/test/**', '**/generated/**', '**/*.min.js'",
                    "Note: the following are always excluded regardless of --exclude:",
                    "  .git/**, .gradle/**, .idea/**, .vscode/**, build/**, out/**,",
                    "  target/**, .mvn/**, node_modules/**, **/.DS_Store, **/Thumbs.db"
            }
    )
    private List<String> excludePatterns = new ArrayList<>();

    @Option(names = {"-o", "--output"}, description = "Output .md file path.")
    private Path outputPath;

    @Option(names = {"--split"}, description = "Write one .md per leaf directory instead of one combined file.")
    private boolean split = false;

    @Option(names = {"--output-dir"}, description = "Directory for split .md files.")
    private Path outputDir;

    @Option(names = {"--branch"}, description = "Branch or tag to use (remote repos only). Default: repo default branch.")
    private String branch;

    @Option(names = {"--no-toc"},  description = "Omit the Table of Contents.")
    private boolean noToc = false;

    @Option(names = {"--no-tree"}, description = "Omit the ASCII file tree.")
    private boolean noTree = false;

    @Option(names = {"--max-tokens"}, description = "Warn if estimated tokens exceed this. Default: 200000.")
    private int maxTokens = 200_000;

    @Option(names = {"--max-file-kb"}, description = "Skip files larger than this KB. Default: 500.")
    private int maxFileSizeKb = 500;

    @Option(
            names = {"--strip-comments"},
            description = {
                    "Strip // and /* */ comments from supported source files.",
                    "Supported: java, kotlin, groovy, scala, javascript, typescript,",
                    "  tsx, jsx, c, cpp, csharp, swift, go, rust, php, fsharp.",
                    "Not supported (returned unchanged): python, yaml, toml, bash, ruby.",
                    "Caveats: the stripper uses a lightweight state machine and may",
                    "  incorrectly remove comment-like sequences inside regex literals",
                    "  (JavaScript/TypeScript) or template strings. Verify output when",
                    "  stripping is enabled for JS/TS files."
            }
    )
    private boolean stripComments = false;

    @Option(names = {"--verbose", "-v"}, description = "Print each included/excluded file to stderr.")
    private boolean verbose = false;

    @Option(names = {"--dry-run"}, description = "List matched files and token estimate — no output written.")
    private boolean dryRun = false;

    @Option(names = {"--auto-chunk"}, description = "Split output into multiple .md files, each within --chunk-tokens budget.")
    private boolean autoChunk = false;

    @Option(names = {"--chunk-tokens"}, description = "Token budget per chunk when --auto-chunk is enabled. Default: 150000.")
    private long chunkTokens = 150_000L;

    @Option(
            names = {"--token"},
            description = "GitHub Personal Access Token (PAT) for private repos and higher rate limits (5,000 req/hour vs 60)."
    )
    private String token;

    // -------------------------------------------------------------------------

    @Override
    public Integer call() {
        try {
            if (GitHubSource.looksLikeGitHub(repoArg)) {
                return runRemote();
            } else {
                return runLocal();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            if (verbose) e.printStackTrace(System.err);
            return 1;
        }
    }

    // -------------------------------------------------------------------------
    // Remote (GitHub) mode
    // -------------------------------------------------------------------------

    private int runRemote() throws Exception {
        GitHubSource source = GitHubSource.parse(repoArg, branch);

        System.out.println("Remote repo : " + source.getLabel());

        if (dryRun) return runRemoteDryRun(source);

        ProcessingConfig config = buildRemoteConfig(source);
        GitHubApiClient client  = new GitHubApiClient(token);

        ProcessingResult result = new GitHubRepoProcessor(source, config, client).process();
        printSummary(result);
        return result.hasWarnings() ? 2 : 0;
    }

    private int runRemoteDryRun(GitHubSource source) throws Exception {
        Path dummyOut = Paths.get(System.getProperty("user.dir"), "__dryrun__.md");
        ProcessingConfig config = buildRemoteConfig(source, dummyOut);
        GitHubApiClient client  = new GitHubApiClient(token);

        GitHubFileCollector collector = new GitHubFileCollector(source, config, client);
        List<SourceFile> files = collector.collect();

        printDryRunSummary(files, source.getLabel());
        return files.isEmpty() ? 1 : 0;
    }

    private ProcessingConfig buildRemoteConfig(GitHubSource source) {
        return buildRemoteConfig(source, resolveOutputPath(source.getRepo()));
    }

    private ProcessingConfig buildRemoteConfig(GitHubSource source, Path out) {
        // Use a synthetic local path for the config — only getRepoPath().getFileName()
        // is used in the output header, which we override via repoLabel anyway.
        return ProcessingConfig.builder()
                .repoPath(Paths.get(source.getRepo()))
                .repoLabel(source.getLabel())
                .includePatterns(includePatterns)
                .excludePatterns(excludePatterns)
                .outputPath(out)
                .splitByDirectory(false)  // split not supported in remote mode yet
                .includeToc(!noToc)
                .includeTree(!noTree)
                .maxTokens(maxTokens)
                .maxFileSizeKb(maxFileSizeKb)
                .stripComments(stripComments)
                .verbose(verbose)
                .build();
    }

    // -------------------------------------------------------------------------
    // Local mode
    // -------------------------------------------------------------------------

    private int runLocal() throws Exception {
        Path resolvedRepo = Paths.get(repoArg).toAbsolutePath().normalize();

        if (dryRun) return runLocalDryRun(resolvedRepo);

        ProcessingConfig config = buildLocalConfig(resolvedRepo);
        ProcessingResult result;
        if (autoChunk) {
            result = new ChunkProcessor(config, chunkTokens).process();
        } else if (split) {
            result = new SplitProcessor(config).process();
        } else {
            result = new RepoProcessor(config).process();
        }
        printSummary(result);
        return result.hasWarnings() ? 2 : 0;
    }

    private int runLocalDryRun(Path resolvedRepo) throws Exception {
        Path dummyOut = resolvedRepo.resolve("__dryrun__.md");
        ProcessingConfig config = ProcessingConfig.builder()
                .repoPath(resolvedRepo)
                .includePatterns(includePatterns)
                .excludePatterns(excludePatterns)
                .outputPath(dummyOut)
                .includeToc(false).includeTree(false)
                .maxTokens(maxTokens).maxFileSizeKb(maxFileSizeKb)
                .stripComments(false).verbose(true)
                .build();

        FileCollector collector = new FileCollector(config);
        List<SourceFile> files  = collector.collect();
        printDryRunSummary(files, resolvedRepo.toString());
        return files.isEmpty() ? 1 : 0;
    }

    private ProcessingConfig buildLocalConfig(Path resolvedRepo) {
        ProcessingConfig.Builder b = ProcessingConfig.builder()
                .repoPath(resolvedRepo)
                .includePatterns(includePatterns)
                .excludePatterns(excludePatterns)
                .splitByDirectory(split)
                .includeToc(!noToc).includeTree(!noTree)
                .maxTokens(maxTokens).maxFileSizeKb(maxFileSizeKb)
                .stripComments(stripComments).verbose(verbose);

        if (split) {
            b.outputDir(resolveOutputDir(resolvedRepo.getFileName().toString()));
        } else {
            b.outputPath(resolveOutputPath(
                    resolvedRepo.getFileName() != null
                            ? resolvedRepo.getFileName().toString() : "repo"));
        }
        return b.build();
    }

    // -------------------------------------------------------------------------
    // Output path helpers
    // -------------------------------------------------------------------------

    private Path resolveOutputPath(String name) {
        if (outputPath != null) return outputPath.toAbsolutePath().normalize();
        return Paths.get(System.getProperty("user.dir")).resolve(name + "-context.md");
    }

    private Path resolveOutputDir(String name) {
        if (outputDir != null) return outputDir.toAbsolutePath().normalize();
        return Paths.get(System.getProperty("user.dir")).resolve(name + "-context");
    }

    // -------------------------------------------------------------------------
    // Summary + dry-run printing
    // -------------------------------------------------------------------------

    private void printSummary(ProcessingResult result) {
        System.out.println();
        System.out.println("md-inator complete");

        if (result.isSplit()) {
            System.out.println("  Output dir : " + result.getOutputFiles().get(0).getPath().getParent());
            System.out.printf ("  Files      : %d source files -> %d .md files%n",
                    result.getIncludedFileCount(), result.getOutputFiles().size());
            System.out.printf ("  Total size : %.1f KB  (~%,d tokens estimated)%n",
                    result.getOutputSizeBytes() / 1024.0, result.getEstimatedTokens());
            System.out.println();
            System.out.println("  Generated files:");
            for (ProcessingResult.OutputFile of : result.getOutputFiles()) {
                System.out.printf("    %-50s  %2d file(s)  ~%,6d tokens%n",
                        of.getPath().getFileName(), of.getFileCount(), of.getEstimatedTokens());
            }
        } else {
            ProcessingResult.OutputFile of = result.getOutputFiles().get(0);
            System.out.println("  Output  : " + of.getPath());
            System.out.printf ("  Files   : %d included, %d excluded%n",
                    result.getIncludedFileCount(), result.getExcludedFileCount());
            System.out.printf ("  Size    : %.1f KB%n", result.getOutputSizeBytes() / 1024.0);
            System.out.printf ("  Tokens  : ~%,d (estimated)%n", result.getEstimatedTokens());
        }

        if (result.hasWarnings()) {
            System.out.println();
            result.getWarnings().forEach(w -> System.out.println("  WARNING: " + w));
        }
        System.out.println();
    }

    private void printDryRunSummary(List<SourceFile> files, String source) {
        System.out.println();
        System.out.println("Dry-run results for: " + source);
        System.out.println("  Include : " + includePatterns);
        System.out.println("  Exclude : " + excludePatterns);
        System.out.println();

        if (files.isEmpty()) {
            System.out.println("  No files matched.");
            System.out.println("  Tip: use --verbose to see every file walked and why it was skipped.");
            return;
        }

        long totalBytes = 0;
        for (SourceFile f : files) {
            System.out.printf("  [match]  %-60s  (%s, %.1f KB)%n",
                    f.getRelativePathString(), f.getLanguage(), f.getSizeBytes() / 1024.0);
            totalBytes += f.getSizeBytes();
        }

        long tokens = TokenEstimator.estimateFromBytes(totalBytes);
        System.out.println();
        System.out.printf("  %d file(s)  |  %.1f KB total  |  ~%,d tokens estimated%n",
                files.size(), totalBytes / 1024.0, tokens);
        if (tokens > maxTokens) {
            System.out.printf("  WARNING: estimated tokens (~%,d) exceed --max-tokens (%,d)%n",
                    tokens, maxTokens);
        }
        System.out.println();
    }

    // -------------------------------------------------------------------------

    static class ManifestVersionProvider implements picocli.CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String v = MdinatorCli.class.getPackage().getImplementationVersion();
            return new String[]{ "md-inator " + (v != null ? v : "dev") };
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MdinatorCli()).execute(args);
        System.exit(exitCode);
    }
}