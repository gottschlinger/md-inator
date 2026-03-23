package gottsch.mdinator.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Glob-based file matcher that works correctly on both Windows and Unix.
 *
 * <h2>The Windows problem</h2>
 * Java's {@code FileSystems.getDefault().getPathMatcher("glob:**‌/*.java")}
 * fails on Windows because:
 * <ol>
 *   <li>{@code Path.of("src/main/App.java").toString()} returns
 *       {@code "src\\main\\App.java"} — backslashes.</li>
 *   <li>The glob pattern uses forward slashes, so the matcher never fires.</li>
 * </ol>
 *
 * <h2>Solution</h2>
 * We convert every glob pattern to an equivalent {@link java.util.regex.Pattern}
 * and match against the forward-slash-normalised relative path string directly,
 * never passing a {@code Path} object to the matcher.
 * The {@code PathMatcher} is kept as a secondary attempt for correctness on Unix,
 * but the regex is the authoritative engine on Windows.
 */
public final class GlobMatcher {

    /** Always-excluded paths — never descend into or include files under these. */
    public static final List<String> DEFAULT_EXCLUDES = List.of(
        ".git/**",
        ".gradle/**",
        ".idea/**",
        ".vscode/**",
        "build/**",
        "out/**",
        "target/**",
        ".mvn/**",
        "node_modules/**",
        "**/.DS_Store",
        "**/Thumbs.db"
    );

    private final List<CompiledPattern> includePatterns;
    private final List<CompiledPattern> excludePatterns;

    public GlobMatcher(List<String> includes, List<String> excludes) {
        List<String> allExcludes = new ArrayList<>(DEFAULT_EXCLUDES);
        allExcludes.addAll(excludes);
        this.includePatterns = compile(includes);
        this.excludePatterns = compile(allExcludes);
    }

    /**
     * Returns {@code true} if the relative path should be included.
     *
     * @param relative path relative to the repo root (any OS separator)
     */
    public boolean matches(Path relative) {
        String rel = toForwardSlash(relative);
        return matchesAny(rel, includePatterns)
            && !matchesAny(rel, excludePatterns);
    }

    /**
     * Returns {@code true} if a directory should be descended into.
     */
    public boolean shouldDescend(Path relativeDir) {
        // Append a dummy filename so "build/**" patterns trigger on the dir itself
        String probe = toForwardSlash(relativeDir) + "/x";
        return !matchesAny(probe, excludePatterns);
    }

    // -------------------------------------------------------------------------

    private static boolean matchesAny(String rel, List<CompiledPattern> patterns) {
        for (CompiledPattern cp : patterns) {
            if (cp.matches(rel)) return true;
        }
        return false;
    }

    private static List<CompiledPattern> compile(List<String> globs) {
        List<CompiledPattern> result = new ArrayList<>(globs.size() * 2);
        for (String glob : globs) {
            String g = glob.replace('\\', '/');
            result.add(new CompiledPattern(g));
            // Also add "**/" prefix so a bare "*.java" matches "src/main/App.java"
            if (!g.startsWith("**/") && !g.startsWith("**")) {
                result.add(new CompiledPattern("**/" + g));
            }
        }
        return result;
    }

    /** Convert a relative {@link Path} to a forward-slash string on any OS. */
    private static String toForwardSlash(Path p) {
        return p.toString().replace('\\', '/');
    }

    // -------------------------------------------------------------------------

    /**
     * A compiled glob pattern with both a regex (primary, platform-independent)
     * and a {@link PathMatcher} (secondary, Unix-only fallback).
     */
    private static final class CompiledPattern {

        private final Pattern regex;          // always works
        private final PathMatcher nioMatcher; // works on Unix; may fail on Windows
        private final String glob;

        CompiledPattern(String glob) {
            this.glob       = glob;
            this.regex      = globToRegex(glob);
            this.nioMatcher = buildNioMatcher(glob);
        }

        boolean matches(String forwardSlashRel) {
            // Primary: regex against the normalised string — works on all platforms
            if (regex != null && regex.matcher(forwardSlashRel).matches()) return true;

            // Secondary: NIO PathMatcher (belt-and-suspenders on Unix)
            if (nioMatcher != null) {
                try {
                    Path p = Path.of(forwardSlashRel);
                    if (nioMatcher.matches(p)) return true;
                    // Also try filename alone
                    Path name = p.getFileName();
                    if (name != null && nioMatcher.matches(name)) return true;
                } catch (Exception ignored) { /* skip */ }
            }

            return false;
        }

        private static PathMatcher buildNioMatcher(String glob) {
            try {
                return FileSystems.getDefault().getPathMatcher("glob:" + glob);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Convert a glob pattern (forward-slash, **-style) to a Java regex.
         *
         * <p>Rules:
         * <ul>
         *   <li>{@code **&#47;} at the start or mid-pattern - match zero or more path segments</li>
         *   <li>{@code &#47;**} at end, or bare {@code **} - match anything including slashes</li>
         *   <li>{@code *}   - match anything within one segment (no slash)</li>
         *   <li>{@code ?}   - match a single character (no slash)</li>
         *   <li>All other regex metacharacters are escaped</li>
         * </ul>
         */
        static Pattern globToRegex(String glob) {
            try {
                StringBuilder sb = new StringBuilder("^");
                int i = 0;
                while (i < glob.length()) {
                    char c = glob.charAt(i);
                    if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        boolean trailSlash = (i + 2 < glob.length() && glob.charAt(i + 2) == '/');
                        if (trailSlash) {
                            // **/ → match zero or more path segments (e.g. "**/*.java")
                            sb.append("(?:.+/)?");
                            i += 3;
                        } else {
                            // ** at end or without trailing slash (e.g. "build/**", ".git/**")
                            sb.append(".*");
                            i += 2;
                        }
                    } else if (c == '*') {
                        sb.append("[^/]*");
                        i++;
                    } else if (c == '?') {
                        sb.append("[^/]");
                        i++;
                    } else if (c == '.') {
                        sb.append("\\.");
                        i++;
                    } else if ("\\.[]{}()+^$|".indexOf(c) >= 0) {
                        sb.append('\\').append(c);
                        i++;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                sb.append("$");
                // Case-insensitive for Windows filesystem compatibility
                return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String toString() { return "CompiledPattern(" + glob + ")"; }
    }
}
