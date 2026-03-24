package gottsch.mdinator.github;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.SourceFile;
import gottsch.mdinator.util.CommentStripper;
import gottsch.mdinator.util.GlobMatcher;
import gottsch.mdinator.util.LanguageDetector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects files from a public GitHub repository via the GitHub REST API.
 *
 * <h2>Strategy</h2>
 * <ol>
 *   <li>Fetch the full recursive git tree in a single API call.</li>
 *   <li>Parse the JSON to extract blob (file) paths and sizes.</li>
 *   <li>Apply glob include/exclude patterns — same {@link GlobMatcher} as
 *       local mode.</li>
 *   <li>Fetch matched file contents individually via the Contents API,
 *       respecting the max-file-kb limit.</li>
 * </ol>
 *
 * <h2>Rate limits</h2>
 * Unauthenticated: 60 requests/hour.  One request for the tree + one per
 * matched file.  A warning is emitted if the matched file count approaches
 * the limit. Adding a PAT (--token) raises the limit to 5,000/hour.
 *
 * @author Mark Gottschling on March 21, 2026
 */
public final class GitHubFileCollector {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final GitHubSource source;
    private final ProcessingConfig config;
    private final GitHubApiClient client;

    private int excludedCount = 0;

    public GitHubFileCollector(GitHubSource source, ProcessingConfig config,
                               GitHubApiClient client) {
        this.source = source;
        this.config = config;
        this.client = client;
    }

    public List<SourceFile> collect() throws IOException {
        String branch = source.getBranchOrDefault();
        if (config.isVerbose()) {
            System.err.println("[github] fetching tree: " + source.getLabel());
        }

        String treeJson = client.getTree(source.getOwner(), source.getRepo(), branch);
        JsonNode root = MAPPER.readTree(treeJson);

        // Check for truncated response
        if (root.path("truncated").asBoolean(false)) {
            System.err.println("[WARN] GitHub returned a truncated tree — "
                    + "some files may be missing. Consider narrowing your --include patterns.");
        }

        GlobMatcher globMatcher = new GlobMatcher(
                config.getIncludePatterns(), config.getExcludePatterns());

        long maxBytes = (long) config.getMaxFileSizeKb() * 1024L;
        List<TreeEntry> matched = new ArrayList<>();

        for (JsonNode item : root.path("tree")) {
            String entryPath = item.path("path").asText();
            String type      = item.path("type").asText();
            long   size      = item.path("size").asLong(0);
            String sha       = item.path("sha").asText();

            if (!"blob".equals(type)) continue;

            Path relativePath = Path.of(entryPath);

            if (!globMatcher.matches(relativePath)) {
                if (config.isVerbose()) System.err.println("[exclude  ] " + entryPath);
                excludedCount++;
                continue;
            }

            if (size > maxBytes) {
                System.err.printf("[SKIP size] %s (%.1f KB > limit %d KB)%n",
                        entryPath, size / 1024.0, config.getMaxFileSizeKb());
                excludedCount++;
                continue;
            }

            matched.add(new TreeEntry(entryPath, size, sha));
            if (config.isVerbose()) System.err.println("[include  ] " + entryPath);
        }

        if (matched.size() > 50) {
            System.err.printf(
                    "[WARN] Fetching %d files. Unauthenticated GitHub API allows 60 requests/hour "
                            + "(1 used for tree). Consider adding --token for a 5,000/hour limit.%n",
                    matched.size());
        }

        List<SourceFile> results = new ArrayList<>();
        for (TreeEntry entry : matched) {
            if (config.isVerbose()) {
                System.err.println("[fetch    ] " + entry.path);
            }
            try {
                String raw = client.getBlob(
                        source.getOwner(), source.getRepo(), entry.sha);

                Path relPath   = Path.of(entry.path);
                String lang    = LanguageDetector.detect(relPath);
                String content = config.isStripComments()
                        ? CommentStripper.strip(raw, lang)
                        : raw;

                results.add(new SourceFile(
                        Path.of("/github/" + source.getOwner() + "/" + source.getRepo())
                                .resolve(entry.path),
                        relPath,
                        lang,
                        content,
                        entry.size
                ));
            } catch (IOException e) {
                System.err.println("[WARN] Could not fetch " + entry.path + ": " + e.getMessage());
                excludedCount++;
            }
        }

        results.sort(Comparator.comparing(SourceFile::getRelativePathString));
        return results;
    }

    public int getExcludedCount() { return excludedCount; }

    private record TreeEntry(String path, long size, String sha) {}
}