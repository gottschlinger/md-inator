package gottsch.mdinator;

import gottsch.mdinator.util.GlobMatcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobMatcherTest {

    // Helper: create a path the same way FileCollector does on Windows
    // (root.relativize(file) produces backslash paths on Windows)
    private static Path winPath(String forwardSlash) {
        // Simulate what Windows gives us: backslash separators
        // Path.of on Linux ignores backslashes, so we test the string-level logic
        // by passing paths that have already been through toForwardSlash stripping.
        return Path.of(forwardSlash);
    }

    // --- Basic **/*.ext patterns ---

    @Test
    void matchesDeepJavaFile() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("src/main/java/com/example/App.java"))).isTrue();
    }

    @Test
    void matchesShallowJavaFile() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("App.java"))).isTrue();
    }

    @Test
    void matchesSingleDirJavaFile() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("src/App.java"))).isTrue();
    }

    @Test
    void matchesFourLevelsDeep() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("a/b/c/d/Foo.java"))).isTrue();
    }

    @Test
    void doesNotMatchWrongExtension() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("src/main/App.kt"))).isFalse();
        assertThat(m.matches(winPath("src/main/App.java.bak"))).isFalse();
    }

    // --- Bare *.ext pattern (no leading **/) ---

    @Test
    void barePatternMatchesDeepFile() {
        GlobMatcher m = new GlobMatcher(List.of("*.java"), List.of());
        assertThat(m.matches(winPath("src/main/App.java"))).isTrue();
        assertThat(m.matches(winPath("App.java"))).isTrue();
    }

    // --- Anchored src/**/*.java ---

    @Test
    void anchoredPatternMatchesUnderSrc() {
        GlobMatcher m = new GlobMatcher(List.of("src/**/*.java"), List.of());
        assertThat(m.matches(winPath("src/main/App.java"))).isTrue();
        assertThat(m.matches(winPath("src/main/java/com/App.java"))).isTrue();
    }

    @Test
    void anchoredPatternDoesNotMatchOutsideSrc() {
        GlobMatcher m = new GlobMatcher(List.of("src/**/*.java"), List.of());
        assertThat(m.matches(winPath("other/App.java"))).isFalse();
    }

    // --- Excludes ---

    @Test
    void excludeOverridesInclude() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("**/test/**"));
        assertThat(m.matches(winPath("src/main/App.java"))).isTrue();
        assertThat(m.matches(winPath("src/test/AppTest.java"))).isFalse();
    }

    @Test
    void defaultExcludesBuildDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.matches(winPath("build/classes/App.java"))).isFalse();
        assertThat(m.shouldDescend(Path.of("build"))).isFalse();
    }

    @Test
    void defaultExcludesGitDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*"), List.of());
        assertThat(m.shouldDescend(Path.of(".git"))).isFalse();
        assertThat(m.matches(winPath(".git/config"))).isFalse();
    }

    @Test
    void srcDirIsDescended() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        assertThat(m.shouldDescend(Path.of("src"))).isTrue();
        assertThat(m.shouldDescend(Path.of("src/main"))).isTrue();
        assertThat(m.shouldDescend(Path.of("src/main/java"))).isTrue();
    }

    // --- Multiple patterns ---

    @Test
    void multipleIncludePatterns() {
        GlobMatcher m = new GlobMatcher(
            List.of("**/*.java", "**/*.gradle", "**/*.yml"), List.of());
        assertThat(m.matches(winPath("build.gradle"))).isTrue();
        assertThat(m.matches(winPath(".github/workflows/ci.yml"))).isTrue();
        assertThat(m.matches(winPath("README.md"))).isFalse();
    }

    // ── shouldDescend edge cases ──────────────────────────────────────────────

    @Test
    void shouldNotDescendIntoDirectlyExcludedDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("foo/**"));
        assertThat(m.shouldDescend(Path.of("foo"))).isFalse();
    }

    @Test
    void shouldNotDescendIntoNestedExcludedDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("foo/**"));
        assertThat(m.shouldDescend(Path.of("foo/bar"))).isFalse();
    }

    @Test
    void shouldDescendIntoSiblingOfExcludedDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("foo/**"));
        assertThat(m.shouldDescend(Path.of("bar"))).isTrue();
    }

    @Test
    void doubleStarPatternExcludesNestedDir() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("**/foo/**"));
        assertThat(m.shouldDescend(Path.of("src/main/foo"))).isFalse();
        assertThat(m.shouldDescend(Path.of("foo"))).isFalse();
    }

    @Test
    void doubleStarPatternDoesNotExcludeSiblings() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("**/foo/**"));
        assertThat(m.shouldDescend(Path.of("src/main/bar"))).isTrue();
        assertThat(m.shouldDescend(Path.of("bar"))).isTrue();
    }

    @Test
    void excludedDirFilesAreAlsoNotMatched() {
        // Consistency check — if shouldDescend returns false, matches should
        // also return false for files inside that directory
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("foo/**"));
        assertThat(m.shouldDescend(Path.of("foo"))).isFalse();
        assertThat(m.matches(Path.of("foo/Bar.java"))).isFalse();
    }

    @Test
    void deeplyNestedExcludeConsistentWithFileMatching() {
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("**/foo/**"));
        assertThat(m.shouldDescend(Path.of("src/main/foo"))).isFalse();
        assertThat(m.matches(Path.of("src/main/foo/Bar.java"))).isFalse();
    }

    @Test
    void excludeWithExactDirNameDoesNotAffectSimilarNames() {
        // "foo/**" should not exclude "foobar/" or "barfoo/"
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of("foo/**"));
        assertThat(m.shouldDescend(Path.of("foobar"))).isTrue();
        assertThat(m.shouldDescend(Path.of("barfoo"))).isTrue();
    }

    @Test
    void multipleExcludePatternsBothApply() {
        GlobMatcher m = new GlobMatcher(
                List.of("**/*.java"),
                List.of("foo/**", "**/bar/**"));
        assertThat(m.shouldDescend(Path.of("foo"))).isFalse();
        assertThat(m.shouldDescend(Path.of("src/bar"))).isFalse();
        assertThat(m.shouldDescend(Path.of("src/main"))).isTrue();
    }

    @Test
    void defaultExcludesAreConsistentBetweenDescendAndMatch() {
        // Every default-excluded dir should be pruned by shouldDescend
        // AND have its files excluded by matches() — verify they agree
        GlobMatcher m = new GlobMatcher(List.of("**/*.java"), List.of());
        for (String excluded : List.of("build", ".git", ".gradle", ".idea",
                "out", "target", "node_modules")) {
            assertThat(m.shouldDescend(Path.of(excluded)))
                    .as("shouldDescend(\"%s\") should be false", excluded)
                    .isFalse();
            assertThat(m.matches(Path.of(excluded + "/Foo.java")))
                    .as("matches(\"%s/Foo.java\") should be false", excluded)
                    .isFalse();
        }
    }
}
