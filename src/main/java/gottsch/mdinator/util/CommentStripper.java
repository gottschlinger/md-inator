package gottsch.mdinator.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Optional stripping of single-line ({@code //}) and block ({@code /* ... *\/})
 * comments from C-family source files.
 *
 * <p>Uses a state-machine approach to correctly handle:
 * <ul>
 *   <li>Comments inside string literals</li>
 *   <li>Nested-looking {@code /*} inside block comments</li>
 *   <li>Javadoc blocks ({@code /** ... *\/})</li>
 * </ul>
 *
 * <p>Falls back to returning the original content for non-applicable languages.
 */
public final class CommentStripper {

    /** Languages we can safely strip C-style comments from. */
    private static final Set<String> SUPPORTED = Set.of(
        "java", "kotlin", "groovy", "scala", "javascript", "typescript",
        "tsx", "jsx", "c", "cpp", "csharp", "swift", "go", "rust",
        "php", "fsharp"
    );

    // Collapse 3+ blank lines to 2 (cleanup after stripping)
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private CommentStripper() {}

    /**
     * Strip comments from {@code source} if the language is supported.
     * Returns the original string unchanged otherwise.
     */
    public static String strip(String source, String language) {
        if (!SUPPORTED.contains(language)) return source;
        String stripped = stripCStyle(source);
        return BLANK_LINES.matcher(stripped).replaceAll("\n\n");
    }

    private static String stripCStyle(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int len = src.length();

        while (i < len) {
            char c = src.charAt(i);

            // String literal — pass through verbatim
            if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < len) {
                    char sc = src.charAt(i);
                    out.append(sc);
                    if (sc == '\\' && i + 1 < len) {
                        i++;
                        out.append(src.charAt(i));
                    } else if (sc == quote) {
                        break;
                    }
                    i++;
                }
                i++;
                continue;
            }

            // Potential comment start
            if (c == '/' && i + 1 < len) {
                char next = src.charAt(i + 1);

                // Single-line comment
                if (next == '/') {
                    i += 2;
                    while (i < len && src.charAt(i) != '\n') i++;
                    continue;
                }

                // Block comment
                if (next == '*') {
                    i += 2;
                    while (i + 1 < len) {
                        if (src.charAt(i) == '*' && src.charAt(i + 1) == '/') {
                            i += 2;
                            break;
                        }
                        i++;
                    }
                    continue;
                }
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }
}
