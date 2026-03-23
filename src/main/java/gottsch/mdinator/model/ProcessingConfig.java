package gottsch.mdinator.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable configuration for a single md-inator processing run.
 */
public final class ProcessingConfig {

    private final Path repoPath;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final Path outputPath;   // single-file mode
    private final Path outputDir;    // split mode
    private final boolean splitByDirectory;
    private final boolean includeToc;
    private final boolean includeTree;
    private final int maxTokens;
    private final int maxFileSizeKb;
    private final boolean stripComments;
    private final boolean verbose;
    private final String repoLabel;  // display name override (used for remote repos)

    private ProcessingConfig(Builder b) {
        this.repoPath          = b.repoPath;
        this.includePatterns   = Collections.unmodifiableList(new ArrayList<>(b.includePatterns));
        this.excludePatterns   = Collections.unmodifiableList(new ArrayList<>(b.excludePatterns));
        this.outputPath        = b.outputPath;
        this.outputDir         = b.outputDir;
        this.splitByDirectory  = b.splitByDirectory;
        this.includeToc        = b.includeToc;
        this.includeTree       = b.includeTree;
        this.maxTokens         = b.maxTokens;
        this.maxFileSizeKb     = b.maxFileSizeKb;
        this.stripComments     = b.stripComments;
        this.verbose           = b.verbose;
        this.repoLabel         = b.repoLabel;
    }

    public Path getRepoPath()                { return repoPath; }
    public List<String> getIncludePatterns() { return includePatterns; }
    public List<String> getExcludePatterns() { return excludePatterns; }
    public Path getOutputPath()              { return outputPath; }
    public Path getOutputDir()               { return outputDir; }
    public boolean isSplitByDirectory()      { return splitByDirectory; }
    public boolean isIncludeToc()            { return includeToc; }
    public boolean isIncludeTree()           { return includeTree; }
    public int getMaxTokens()                { return maxTokens; }
    public int getMaxFileSizeKb()            { return maxFileSizeKb; }
    public boolean isStripComments()         { return stripComments; }
    public boolean isVerbose()               { return verbose; }
    public String getRepoLabel()             { return repoLabel; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Path repoPath;
        private List<String> includePatterns = new ArrayList<>();
        private List<String> excludePatterns = new ArrayList<>();
        private Path outputPath;
        private Path outputDir;
        private boolean splitByDirectory = false;
        private boolean includeToc       = true;
        private boolean includeTree      = true;
        private int maxTokens            = 200_000;
        private int maxFileSizeKb        = 500;
        private boolean stripComments    = false;
        private boolean verbose          = false;
        private String repoLabel         = null;

        public Builder repoPath(Path v)                { this.repoPath = v; return this; }
        public Builder includePatterns(List<String> v) { this.includePatterns = v; return this; }
        public Builder excludePatterns(List<String> v) { this.excludePatterns = v; return this; }
        public Builder outputPath(Path v)              { this.outputPath = v; return this; }
        public Builder outputDir(Path v)               { this.outputDir = v; return this; }
        public Builder splitByDirectory(boolean v)     { this.splitByDirectory = v; return this; }
        public Builder includeToc(boolean v)           { this.includeToc = v; return this; }
        public Builder includeTree(boolean v)          { this.includeTree = v; return this; }
        public Builder maxTokens(int v)                { this.maxTokens = v; return this; }
        public Builder maxFileSizeKb(int v)            { this.maxFileSizeKb = v; return this; }
        public Builder stripComments(boolean v)        { this.stripComments = v; return this; }
        public Builder verbose(boolean v)              { this.verbose = v; return this; }
        public Builder repoLabel(String v)             { this.repoLabel = v; return this; }

        public ProcessingConfig build() {
            // repoPath is required for local mode; remote mode sets a synthetic value
            if (repoPath == null) throw new IllegalStateException("repoPath is required");
            if (includePatterns.isEmpty()) throw new IllegalStateException("at least one include pattern is required");
            if (splitByDirectory) {
                if (outputDir == null) throw new IllegalStateException("outputDir is required in split mode");
            } else {
                if (outputPath == null) throw new IllegalStateException("outputPath is required in single-file mode");
            }
            return new ProcessingConfig(this);
        }
    }
}