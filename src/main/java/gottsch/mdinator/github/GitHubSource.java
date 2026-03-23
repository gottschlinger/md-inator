package gottsch.mdinator.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a GitHub repository reference from either a full URL or an
 * {@code owner/repo} shorthand, and carries the resolved owner, repo
 * name, and optional branch.
 *
 * <p>Accepted input forms:
 * <ul>
 *   <li>{@code https://github.com/owner/repo}</li>
 *   <li>{@code https://github.com/owner/repo.git}</li>
 *   <li>{@code http://github.com/owner/repo}</li>
 *   <li>{@code github.com/owner/repo}</li>
 *   <li>{@code owner/repo} (shorthand)</li>
 * </ul>
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class GitHubSource {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://)?github\\.com/([^/]+)/([^/\\.]+?)(?:\\.git)?/?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SHORTHAND_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9][a-zA-Z0-9_.-]*)/([a-zA-Z0-9][a-zA-Z0-9_.-]+)$"
    );

    private final String owner;
    private final String repo;
    private final String branch;

    private GitHubSource(String owner, String repo, String branch) {
        this.owner  = owner;
        this.repo   = repo;
        this.branch = branch;
    }

    public String getOwner()  { return owner; }
    public String getRepo()   { return repo; }

    /** Returns the branch to use, or {@code "HEAD"} if none was specified. */
    public String getBranchOrDefault() {
        return branch != null ? branch : "HEAD";
    }

    public boolean hasBranch() { return branch != null; }

    /**
     * Human-readable label for headers, e.g. {@code owner/repo@branch}.
     */
    public String getLabel() {
        return branch != null
                ? owner + "/" + repo + "@" + branch
                : owner + "/" + repo;
    }

    // -------------------------------------------------------------------------

    /**
     * Parses {@code input} as a GitHub source reference.
     *
     * @param input  raw string supplied by the user
     * @param branch optional branch/tag override; may be null
     * @throws IllegalArgumentException if the input cannot be recognised
     */
    public static GitHubSource parse(String input, String branch) {
        Matcher urlMatcher = URL_PATTERN.matcher(input.trim());
        if (urlMatcher.find()) {
            return new GitHubSource(urlMatcher.group(1), urlMatcher.group(2), branch);
        }

        Matcher shortMatcher = SHORTHAND_PATTERN.matcher(input.trim());
        if (shortMatcher.matches()) {
            return new GitHubSource(shortMatcher.group(1), shortMatcher.group(2), branch);
        }

        throw new IllegalArgumentException(
                "Cannot parse '" + input + "' as a GitHub repository. "
                        + "Expected 'https://github.com/owner/repo' or 'owner/repo'."
        );
    }

    /**
     * Returns true if {@code input} looks like a GitHub URL or owner/repo
     * shorthand rather than a local filesystem path.
     */
    public static boolean looksLikeGitHub(String input) {
        if (input == null) return false;
        String t = input.trim();
        return URL_PATTERN.matcher(t).find() || SHORTHAND_PATTERN.matcher(t).matches();
    }

    @Override
    public String toString() { return getLabel(); }
}