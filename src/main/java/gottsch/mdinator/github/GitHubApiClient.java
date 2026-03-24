package gottsch.mdinator.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper around the GitHub REST API v3.
 *
 * <p>Uses Java 11+ {@link HttpClient} — no extra dependencies needed.
 *
 * <p>Unauthenticated requests are limited to 60/hour by GitHub.
 * A personal access token (PAT) can be supplied to lift this to 5,000/hour
 * (wired in as part of the private-repo feature, roadmap item).
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class GitHubApiClient {

    private static final String API_BASE  = "https://api.github.com";
    private static final String USER_AGENT = "md-inator/1.0 (https://github.com/md-inator)";

    private final HttpClient http;
    private final String token;   // null = unauthenticated

    public GitHubApiClient() {
        this(null);
    }

    public GitHubApiClient(String token) {
        this.token = token;
        this.http  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // -------------------------------------------------------------------------

    /**
     * Fetches the recursive git tree for a repository, returning the raw JSON.
     *
     * <p>Endpoint: {@code GET /repos/{owner}/{repo}/git/trees/{branch}?recursive=1}
     *
     * <p>A single request returns up to 100,000 tree entries — sufficient for
     * any realistic repository. If GitHub truncates the response (very large
     * mono-repos), a warning is embedded in the JSON {@code truncated} field
     * which {@link GitHubFileCollector} checks.
     */
    public String getTree(String owner, String repo, String branch) throws IOException {
        String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1",
                API_BASE, owner, repo, branch);
        return get(url);
    }

    /**
     * Fetches the raw content of a single file.
     *
     * <p>Endpoint: {@code GET /repos/{owner}/{repo}/contents/{path}?ref={branch}}
     *
     * <p>Returns the raw file bytes as a string (decoded from base64 by GitHub).
     * We request {@code application/vnd.github.raw} to get the file content
     * directly without base64 wrapping.
     */
//    public String getFileContent(String owner, String repo, String path, String branch)
//            throws IOException {
//        String url = String.format("%s/repos/%s/%s/contents/%s?ref=%s",
//                API_BASE, owner, repo,
//                path.replace(" ", "%20"),
//                branch);
//        return getRaw(url);
//    }

    public String getBlob(String owner, String repo, String sha) throws IOException {
        String url = String.format("%s/repos/%s/%s/git/blobs/%s",
                API_BASE, owner, repo, sha);
        return getRaw(url); // already uses Accept: application/vnd.github.raw
    }
    // -------------------------------------------------------------------------

    private String get(String url) throws IOException {
        HttpRequest req = baseRequest(url)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        return send(req, url);
    }

    private String getRaw(String url) throws IOException {
        HttpRequest req = baseRequest(url)
                .header("Accept", "application/vnd.github.raw")
                .GET()
                .build();
        return send(req, url);
    }

    private HttpRequest.Builder baseRequest(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private String send(HttpRequest req, String url) throws IOException {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            int status = resp.statusCode();
            if (status == 200) return resp.body();
            if (status == 404) throw new IOException(
                    "Repository or path not found (404): " + url + "\n"
                            + "Check that the repo is public and the path/branch is correct.");
            if (status == 403 || status == 429) throw new IOException(
                    "GitHub API rate limit exceeded (" + status + "). "
                            + "You have " + resp.headers().firstValue("X-RateLimit-Remaining").orElse("?")
                            + " requests remaining. "
                            + "Add a --token to increase the limit to 5,000 requests/hour.");
            throw new IOException(
                    "GitHub API error " + status + " for " + url + ": " + resp.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted: " + url, e);
        }
    }
}