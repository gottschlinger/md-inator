package gottsch.mdinator;

import gottsch.mdinator.github.GitHubSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubSourceTest {

    // --- looksLikeGitHub detection ---

    @Test void detectsFullHttpsUrl() {
        assertThat(GitHubSource.looksLikeGitHub("https://github.com/owner/repo")).isTrue();
    }
    @Test void detectsHttpUrl() {
        assertThat(GitHubSource.looksLikeGitHub("http://github.com/owner/repo")).isTrue();
    }
    @Test void detectsUrlWithoutScheme() {
        assertThat(GitHubSource.looksLikeGitHub("github.com/owner/repo")).isTrue();
    }
    @Test void detectsUrlWithDotGit() {
        assertThat(GitHubSource.looksLikeGitHub("https://github.com/owner/repo.git")).isTrue();
    }
    @Test void detectsShorthand() {
        assertThat(GitHubSource.looksLikeGitHub("owner/repo")).isTrue();
    }
    @Test void doesNotDetectLocalAbsolutePath() {
        assertThat(GitHubSource.looksLikeGitHub("C:\\Dev\\myrepo")).isFalse();
        assertThat(GitHubSource.looksLikeGitHub("/home/user/myrepo")).isFalse();
    }
    @Test void doesNotDetectRelativePath() {
        assertThat(GitHubSource.looksLikeGitHub("./myrepo")).isFalse();
        assertThat(GitHubSource.looksLikeGitHub("myrepo")).isFalse();
    }

    // --- parse: full URL ---

    @Test void parsesFullUrl() {
        GitHubSource s = GitHubSource.parse("https://github.com/torvalds/linux", null);
        assertThat(s.getOwner()).isEqualTo("torvalds");
        assertThat(s.getRepo()).isEqualTo("linux");
        assertThat(s.hasBranch()).isFalse();
        assertThat(s.getBranchOrDefault()).isEqualTo("HEAD");
    }

    @Test void parsesUrlWithDotGit() {
        GitHubSource s = GitHubSource.parse("https://github.com/owner/my-repo.git", null);
        assertThat(s.getRepo()).isEqualTo("my-repo");
    }

    @Test void parsesUrlWithTrailingSlash() {
        GitHubSource s = GitHubSource.parse("https://github.com/owner/repo/", null);
        assertThat(s.getOwner()).isEqualTo("owner");
        assertThat(s.getRepo()).isEqualTo("repo");
    }

    // --- parse: shorthand ---

    @Test void parsesShorthand() {
        GitHubSource s = GitHubSource.parse("torvalds/linux", null);
        assertThat(s.getOwner()).isEqualTo("torvalds");
        assertThat(s.getRepo()).isEqualTo("linux");
    }

    @Test void parsesShorthandWithDashes() {
        GitHubSource s = GitHubSource.parse("my-org/my-repo", null);
        assertThat(s.getOwner()).isEqualTo("my-org");
        assertThat(s.getRepo()).isEqualTo("my-repo");
    }

    // --- branch ---

    @Test void branchIsPreserved() {
        GitHubSource s = GitHubSource.parse("owner/repo", "develop");
        assertThat(s.hasBranch()).isTrue();
        assertThat(s.getBranchOrDefault()).isEqualTo("develop");
    }

    @Test void labelIncludesBranchWhenSet() {
        GitHubSource s = GitHubSource.parse("owner/repo", "feature/xyz");
        assertThat(s.getLabel()).isEqualTo("owner/repo@feature/xyz");
    }

    @Test void labelOmitsBranchWhenNotSet() {
        GitHubSource s = GitHubSource.parse("owner/repo", null);
        assertThat(s.getLabel()).isEqualTo("owner/repo");
    }

    // --- errors ---

    @Test void throwsOnUnrecognisedInput() {
        assertThatThrownBy(() -> GitHubSource.parse("not-a-repo", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot parse");
    }

    @Test void throwsOnNull() {
        assertThatThrownBy(() -> GitHubSource.parse(null, null))
            .isInstanceOf(Exception.class);
    }
}