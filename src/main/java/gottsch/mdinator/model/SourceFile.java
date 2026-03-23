package gottsch.mdinator.model;

import java.nio.file.Path;

/**
 * A single source file that has been matched and read.
 */
public final class SourceFile {

    private final Path absolutePath;
    private final Path relativePath;   // relative to repo root
    private final String language;     // markdown language hint
    private final String content;
    private final long sizeBytes;

    public SourceFile(Path absolutePath, Path relativePath,
                      String language, String content, long sizeBytes) {
        this.absolutePath = absolutePath;
        this.relativePath = relativePath;
        this.language     = language;
        this.content      = content;
        this.sizeBytes    = sizeBytes;
    }

    public Path getAbsolutePath()  { return absolutePath; }
    public Path getRelativePath()  { return relativePath; }
    public String getLanguage()    { return language; }
    public String getContent()     { return content; }
    public long getSizeBytes()     { return sizeBytes; }

    /** Slash-separated relative path string — platform independent. */
    public String getRelativePathString() {
        return relativePath.toString().replace('\\', '/');
    }
}
