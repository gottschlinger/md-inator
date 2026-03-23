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
}
