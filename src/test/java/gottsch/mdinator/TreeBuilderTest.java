package gottsch.mdinator;

import gottsch.mdinator.core.TreeBuilder;
import gottsch.mdinator.model.SourceFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeBuilderTest {

    private static SourceFile file(String rel) {
        Path r = Path.of(rel);
        return new SourceFile(r, r, "java", "", 0L);
    }

    @Test
    void buildsSingleFileTree() {
        List<SourceFile> files = List.of(file("src/main/App.java"));
        String tree = TreeBuilder.build(files, "myrepo");
        assertThat(tree).contains("myrepo/");
        assertThat(tree).contains("src/");
        assertThat(tree).contains("App.java");
    }

    @Test
    void sortsDirsBeforeFiles() {
        List<SourceFile> files = List.of(
            file("src/main/App.java"),
            file("src/main/service/UserService.java"),
            file("build.gradle")
        );
        String tree = TreeBuilder.build(files, "repo");
        // src/ directory should appear before build.gradle at root level
        int srcIdx = tree.indexOf("src/");
        int gradleIdx = tree.indexOf("build.gradle");
        assertThat(srcIdx).isLessThan(gradleIdx);
    }

    @Test
    void wrapsInCodeFence() {
        String tree = TreeBuilder.build(List.of(file("Foo.java")), "r");
        assertThat(tree).startsWith("```\n");
        assertThat(tree).endsWith("```\n");
    }

    @Test
    void multipleFilesShareDirectoryNode() {
        List<SourceFile> files = List.of(
            file("src/A.java"),
            file("src/B.java")
        );
        String tree = TreeBuilder.build(files, "repo");
        // "src/" should appear only once
        long count = tree.lines().filter(l -> l.contains("src/")).count();
        assertThat(count).isEqualTo(1);
    }
}
