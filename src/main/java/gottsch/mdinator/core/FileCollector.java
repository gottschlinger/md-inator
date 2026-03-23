package gottsch.mdinator.core;

import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.SourceFile;
import gottsch.mdinator.util.CommentStripper;
import gottsch.mdinator.util.GlobMatcher;
import gottsch.mdinator.util.MdinatorIgnore;
import gottsch.mdinator.util.LanguageDetector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Walks a repository directory tree, applies include/exclude glob patterns,
 * reads file contents, and returns an ordered list of {@link SourceFile}s.
 *
 * <p>If a {@code .mdinatorignore} file exists at the repo root its patterns
 * are merged with any CLI {@code --exclude} flags (CLI takes priority).
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class FileCollector {

    private final ProcessingConfig config;
    private final GlobMatcher matcher;

    // Counters
    private int excluded = 0;

    public FileCollector(ProcessingConfig config) {
        this.config = config;
        // Load .mdinatorignore from repo root and merge with CLI excludes
        MdinatorIgnore ignoreFile = MdinatorIgnore.load(config.getRepoPath());
        if (ignoreFile.isFound() && config.isVerbose()) {
            System.err.println("[ignore   ] loaded .mdinatorignore ("
                    + ignoreFile.getPatterns().size() + " pattern(s))");
        }
        List<String> mergedExcludes = MdinatorIgnore.merge(
                config.getExcludePatterns(), ignoreFile);
        this.matcher = new GlobMatcher(config.getIncludePatterns(), mergedExcludes);
    }

    /**
     * Performs the walk and returns matched files sorted by relative path.
     *
     * @throws IOException if the repo root cannot be read
     */
    public List<SourceFile> collect() throws IOException {
        Path root = config.getRepoPath();
        if (!Files.isDirectory(root)) {
            throw new IOException("Repo path is not a directory: " + root);
        }

        List<SourceFile> results = new ArrayList<>();
        long maxBytes = (long) config.getMaxFileSizeKb() * 1024L;

        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                Path rel = root.relativize(dir);
                if (matcher.shouldDescend(rel)) {
                    return FileVisitResult.CONTINUE;
                }
                if (config.isVerbose()) {
                    System.err.println("[SKIP dir ] " + rel);
                }
                excluded++;
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path rel = root.relativize(file);

                if (!matcher.matches(rel)) {
                    if (config.isVerbose()) System.err.println("[exclude  ] " + rel);
                    excluded++;
                    return FileVisitResult.CONTINUE;
                }

                // Size guard
                long size = attrs.size();
                if (size > maxBytes) {
                    System.err.printf("[SKIP size] %s (%.1f KB > limit %d KB)%n",
                            rel, size / 1024.0, config.getMaxFileSizeKb());
                    excluded++;
                    return FileVisitResult.CONTINUE;
                }

                try {
                    String raw = Files.readString(file, StandardCharsets.UTF_8);
                    String language = LanguageDetector.detect(file);
                    String content  = config.isStripComments()
                            ? CommentStripper.strip(raw, language)
                            : raw;

                    results.add(new SourceFile(file, rel, language, content, size));
                    if (config.isVerbose()) System.err.println("[include  ] " + rel);

                } catch (IOException e) {
                    System.err.println("[WARN] Could not read " + rel + ": " + e.getMessage());
                    excluded++;
                } catch (Exception e) {
                    // Binary / non-UTF-8 file
                    System.err.println("[SKIP bin ] " + rel + " (not UTF-8 text)");
                    excluded++;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("[WARN] Cannot access " + file + ": " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        // Stable sort: alphabetical by relative path
        results.sort(Comparator.comparing(SourceFile::getRelativePathString));
        return results;
    }

    /** Number of files that were skipped (excludes, size guards, read errors). */
    public int getExcludedCount() { return excluded; }
}