package gottsch.mdinator;

import gottsch.mdinator.util.MdinatorIgnore;
import gottsch.mdinator.core.FileCollector;
import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MdinatorIgnore}.
 *
 * @author Mark Gottschling on March 21, 2026
 */
class MdinatorIgnoreTest {

    @TempDir Path repoRoot;

    // --- loading ---

    @Test
    void returnsEmptyWhenFileAbsent() {
        MdinatorIgnore result = MdinatorIgnore.load(repoRoot);
        assertThat(result.isFound()).isFalse();
        assertThat(result.getPatterns()).isEmpty();
    }

    @Test
    void loadsPatterns() throws IOException {
        write(".mdinatorignore",
                "# comment\n",
                "\n",
                "**/*.log\n",
                "**/generated/**\n"
        );
        MdinatorIgnore result = MdinatorIgnore.load(repoRoot);
        assertThat(result.isFound()).isTrue();
        assertThat(result.getPatterns()).containsExactly("**/*.log", "**/generated/**");
    }

    @Test
    void ignoresCommentLines() throws IOException {
        write(".mdinatorignore", "# this is a comment\n", "**/*.tmp\n");
        MdinatorIgnore result = MdinatorIgnore.load(repoRoot);
        assertThat(result.getPatterns()).containsExactly("**/*.tmp");
    }

    @Test
    void ignoresBlankLines() throws IOException {
        write(".mdinatorignore", "\n", "  \n", "**/*.bak\n", "\n");
        MdinatorIgnore result = MdinatorIgnore.load(repoRoot);
        assertThat(result.getPatterns()).containsExactly("**/*.bak");
    }

    @Test
    void handlesEmptyFile() throws IOException {
        write(".mdinatorignore", "");
        MdinatorIgnore result = MdinatorIgnore.load(repoRoot);
        assertThat(result.isFound()).isTrue();
        assertThat(result.getPatterns()).isEmpty();
    }

    // --- merge priority ---

    @Test
    void cliExcomeFirstInMerge() throws IOException {
        write(".mdinatorignore", "**/generated/**\n");
        MdinatorIgnore ignoreFile = MdinatorIgnore.load(repoRoot);

        List<String> merged = MdinatorIgnore.merge(List.of("**/test/**"), ignoreFile);

        assertThat(merged).containsExactly("**/test/**", "**/generated/**");
        // CLI pattern is first — it has higher priority
        assertThat(merged.get(0)).isEqualTo("**/test/**");
    }

    @Test
    void mergeWithNoCliExcludes() throws IOException {
        write(".mdinatorignore", "**/*.log\n", "**/tmp/**\n");
        MdinatorIgnore ignoreFile = MdinatorIgnore.load(repoRoot);

        List<String> merged = MdinatorIgnore.merge(List.of(), ignoreFile);
        assertThat(merged).containsExactly("**/*.log", "**/tmp/**");
    }

    @Test
    void mergeWithNoIgnoreFile() {
        MdinatorIgnore ignoreFile = MdinatorIgnore.load(repoRoot); // no file
        List<String> merged = MdinatorIgnore.merge(List.of("**/test/**"), ignoreFile);
        assertThat(merged).containsExactly("**/test/**");
    }

    // --- integration with FileCollector ---

    @Test
    void ignoredFilesAreExcludedFromCollection() throws IOException {
        // Create source files
        Path src = repoRoot.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"),       "class App {}");
        Files.writeString(src.resolve("App.java.bak"),   "old backup");
        Files.writeString(src.resolve("debug.log"),      "log output");

        // .mdinatorignore excludes backups and logs
        write(".mdinatorignore", "**/*.bak\n", "**/*.log\n");

        ProcessingConfig config = ProcessingConfig.builder()
                .repoPath(repoRoot)
                .includePatterns(List.of("**/*"))
                .excludePatterns(List.of())
                .outputPath(repoRoot.resolve("out.md"))
                .includeToc(false).includeTree(false)
                .maxTokens(200_000).maxFileSizeKb(500)
                .stripComments(false).verbose(false)
                .build();

        FileCollector collector = new FileCollector(config);
        List<SourceFile> files = collector.collect();

        List<String> names = files.stream()
                .map(f -> f.getRelativePathString())
                .toList();
        assertThat(names).contains("src/App.java");
        assertThat(names).doesNotContain("src/App.java.bak", "src/debug.log");
    }

    // -------------------------------------------------------------------------

    private void write(String name, String... lines) throws IOException {
        Path f = repoRoot.resolve(name);
        Files.writeString(f, String.join("", lines));
    }
}