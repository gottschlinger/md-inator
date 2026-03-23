package gottsch.mdinator.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads and parses a {@code .mdinatorignore} file from the repository root.
 *
 * <p>Syntax follows {@code .gitignore} conventions:
 * <ul>
 *   <li>One glob pattern per line</li>
 *   <li>Lines starting with {@code #} are comments and are ignored</li>
 *   <li>Blank lines are ignored</li>
 *   <li>Patterns follow the same glob rules as {@code --exclude} flags</li>
 * </ul>
 *
 * <p>Priority: CLI {@code --exclude} flags take precedence — patterns from
 * {@code .mdinatorignore} are appended after CLI excludes in {@link GlobMatcher}.
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class MdinatorIgnore {

    public static final String FILENAME = ".mdinatorignore";

    private final List<String> patterns;
    private final boolean found;

    private MdinatorIgnore(List<String> patterns, boolean found) {
        this.patterns = Collections.unmodifiableList(patterns);
        this.found    = found;
    }

    /** Returns the parsed exclude patterns (never null, may be empty). */
    public List<String> getPatterns() { return patterns; }

    /** Returns true if a {@code .mdinatorignore} file was found and read. */
    public boolean isFound() { return found; }

    // -------------------------------------------------------------------------

    /**
     * Attempts to read {@code .mdinatorignore} from the given repo root.
     * Returns an empty instance (no patterns, {@code found=false}) if the
     * file does not exist or cannot be read.
     */
    public static MdinatorIgnore load(Path repoRoot) {
        Path ignoreFile = repoRoot.resolve(FILENAME);

        if (!Files.exists(ignoreFile)) {
            return new MdinatorIgnore(List.of(), false);
        }

        try {
            List<String> patterns = new ArrayList<>();
            for (String line : Files.readAllLines(ignoreFile, StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                patterns.add(trimmed);
            }
            return new MdinatorIgnore(patterns, true);
        } catch (IOException e) {
            System.err.println("[WARN] Could not read " + FILENAME + ": " + e.getMessage());
            return new MdinatorIgnore(List.of(), false);
        }
    }

    /**
     * Merges CLI exclude patterns with {@code .mdinatorignore} patterns.
     * CLI patterns come first (higher priority — they are evaluated first
     * by {@link GlobMatcher}).
     *
     * @param cliExcludes  patterns from {@code --exclude} flags
     * @param ignoreFile   loaded {@link MdinatorIgnore} instance
     * @return combined list, CLI excludes first
     */
    public static List<String> merge(List<String> cliExcludes, MdinatorIgnore ignoreFile) {
        List<String> merged = new ArrayList<>(cliExcludes);
        merged.addAll(ignoreFile.getPatterns());
        return merged;
    }
}