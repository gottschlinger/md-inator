package gottsch.mdinator.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a single md-inator processing run.
 *
 * <p>In split mode, {@link #getOutputFiles()} contains one entry per
 * generated file.  In single-file mode it contains exactly one entry and
 * {@link #getOutputPath()} is a convenience accessor for that entry's path.
 */
public final class ProcessingResult {

    /** Per-file summary used in split mode. */
    public static final class OutputFile {
        private final Path path;
        private final String directoryLabel;   // leaf dir name shown in summary
        private final int fileCount;
        private final long sizeBytes;
        private final long estimatedTokens;

        public OutputFile(Path path, String directoryLabel,
                          int fileCount, long sizeBytes, long estimatedTokens) {
            this.path             = path;
            this.directoryLabel   = directoryLabel;
            this.fileCount        = fileCount;
            this.sizeBytes        = sizeBytes;
            this.estimatedTokens  = estimatedTokens;
        }

        public Path getPath()              { return path; }
        public String getDirectoryLabel()  { return directoryLabel; }
        public int getFileCount()          { return fileCount; }
        public long getSizeBytes()         { return sizeBytes; }
        public long getEstimatedTokens()   { return estimatedTokens; }
    }

    private final List<OutputFile> outputFiles;
    private final int includedFileCount;
    private final int excludedFileCount;
    private final long totalOutputSizeBytes;
    private final long totalEstimatedTokens;
    private final List<String> warnings;

    private ProcessingResult(Builder b) {
        this.outputFiles           = Collections.unmodifiableList(new ArrayList<>(b.outputFiles));
        this.includedFileCount     = b.includedFileCount;
        this.excludedFileCount     = b.excludedFileCount;
        this.totalOutputSizeBytes  = b.totalOutputSizeBytes;
        this.totalEstimatedTokens  = b.totalEstimatedTokens;
        this.warnings              = Collections.unmodifiableList(new ArrayList<>(b.warnings));
    }

    /** Convenience accessor for single-file mode. */
    public Path getOutputPath() {
        return outputFiles.isEmpty() ? null : outputFiles.get(0).getPath();
    }

    public List<OutputFile> getOutputFiles()     { return outputFiles; }
    public int getIncludedFileCount()            { return includedFileCount; }
    public int getExcludedFileCount()            { return excludedFileCount; }
    public long getOutputSizeBytes()             { return totalOutputSizeBytes; }
    public long getEstimatedTokens()             { return totalEstimatedTokens; }
    public List<String> getWarnings()            { return warnings; }
    public boolean hasWarnings()                 { return !warnings.isEmpty(); }
    public boolean isSplit()                     { return outputFiles.size() > 1; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<OutputFile> outputFiles     = new ArrayList<>();
        private int includedFileCount;
        private int excludedFileCount;
        private long totalOutputSizeBytes;
        private long totalEstimatedTokens;
        private List<String> warnings            = new ArrayList<>();

        public Builder addOutputFile(OutputFile f)  { this.outputFiles.add(f); return this; }
        public Builder includedFileCount(int v)     { this.includedFileCount = v; return this; }
        public Builder excludedFileCount(int v)     { this.excludedFileCount = v; return this; }
        public Builder totalOutputSizeBytes(long v) { this.totalOutputSizeBytes = v; return this; }
        public Builder totalEstimatedTokens(long v) { this.totalEstimatedTokens = v; return this; }
        public Builder addWarning(String w)         { this.warnings.add(w); return this; }

        // Convenience for single-file mode
        public Builder outputPath(Path v) {
            return addOutputFile(new OutputFile(v, v.getFileName().toString(), 0, 0, 0));
        }
        public Builder outputSizeBytes(long v)  { this.totalOutputSizeBytes = v; return this; }
        public Builder estimatedTokens(long v)  { this.totalEstimatedTokens = v; return this; }
        public Builder warnings(List<String> w) { this.warnings = w; return this; }

        public ProcessingResult build() { return new ProcessingResult(this); }
    }
}
