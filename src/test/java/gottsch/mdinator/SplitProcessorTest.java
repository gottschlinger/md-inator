package gottsch.mdinator;

import gottsch.mdinator.core.SplitProcessor;
import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitProcessorTest {

    @TempDir Path repoRoot;
    @TempDir Path outputDir;

    private ProcessingConfig config(String... extraExcludes) throws IOException {
        return ProcessingConfig.builder()
            .repoPath(repoRoot)
            .includePatterns(List.of("**/*.java"))
            .excludePatterns(List.of(extraExcludes))
            .outputDir(outputDir)
            .splitByDirectory(true)
            .includeToc(true)
            .includeTree(true)
            .maxTokens(200_000)
            .maxFileSizeKb(500)
            .stripComments(false)
            .verbose(false)
            .build();
    }

    // --- leaf grouping ---

    @Test
    void oneFilePerLeafDirectory() throws IOException {
        write("src/main/a/Foo.java",   "public class Foo {}");
        write("src/main/a/Bar.java",  "public class Bar {}");
        write("src/main/b/Baz.java",      "public class Baz {}");

        ProcessingResult result = new SplitProcessor(config()).process();

        // Two leaf directories → two output files
        assertThat(result.getOutputFiles()).hasSize(2);
        assertThat(result.getIncludedFileCount()).isEqualTo(3);

        List<String> names = result.getOutputFiles().stream()
            .map(f -> f.getPath().getFileName().toString())
            .toList();
        assertThat(names).containsExactlyInAnyOrder(
            "src.main.a.md",
            "src.main.b.md"
        );
    }

    @Test
    void parentDirsWithNoDirectFilesAreSkipped() throws IOException {
        // Only files are in leaf dirs — no file sits directly in src/ or src/main/
        write("src/main/a/Foo.java", "public class Foo {}");
        write("src/main/b/Bar.java",   "public class Bar {}");

        ProcessingResult result = new SplitProcessor(config()).process();

        assertThat(result.getOutputFiles()).hasSize(2);
        List<String> names = result.getOutputFiles().stream()
            .map(f -> f.getPath().getFileName().toString())
            .toList();
        // src/ and src/main/ produce no output — only the leaf dirs do
        assertThat(names).doesNotContain("src.md", "src.main.md");
        assertThat(names).containsExactlyInAnyOrder(
            "src.main.a.md",
            "src.main.b.md"
        );
    }

    @Test
    void rootLevelFilesWrittenToRootMd() throws IOException {
        write("App.java", "public class App {}");

        ProcessingResult result = new SplitProcessor(config()).process();

        assertThat(result.getOutputFiles()).hasSize(1);
        assertThat(result.getOutputFiles().get(0).getPath().getFileName().toString())
            .isEqualTo("_root.md");
    }

    @Test
    void outputFilesAreWrittenToOutputDir() throws IOException {
        write("src/Foo.java", "public class Foo {}");

        new SplitProcessor(config()).process();

        assertThat(Files.list(outputDir).toList()).isNotEmpty();
    }

    @Test
    void eachOutputFileContainsOnlyItsFilesContent() throws IOException {
        write("src/a/Foo.java", "public class Foo {}");
        write("src/b/Bar.java",    "public class Bar {}");

        ProcessingResult result = new SplitProcessor(config()).process();

        for (ProcessingResult.OutputFile of : result.getOutputFiles()) {
            String content = Files.readString(of.getPath());
            if (of.getPath().getFileName().toString().contains("a")) {
                assertThat(content).contains("Foo");
                assertThat(content).doesNotContain("Bar");
            } else {
                assertThat(content).contains("Bar");
                assertThat(content).doesNotContain("Foo");
            }
        }
    }

    @Test
    void outputFileCountMatchesFileGroupCount() throws IOException {
        write("a/Foo.java", "class Foo {}");
        write("b/Bar.java", "class Bar {}");
        write("c/Baz.java", "class Baz {}");

        ProcessingResult result = new SplitProcessor(config()).process();
        assertThat(result.getOutputFiles()).hasSize(3);
    }

    @Test
    void throwsWhenNoFilesMatch() {
        assertThatThrownBy(() -> new SplitProcessor(config()).process())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("No files matched");
    }

    @Test
    void eachOutputFileContainsHeader() throws IOException {
        write("src/Foo.java", "public class Foo {}");

        ProcessingResult result = new SplitProcessor(config()).process();
        String content = Files.readString(result.getOutputFiles().get(0).getPath());
        assertThat(content).contains("# Repository Context");
        assertThat(content).contains("Generated by md-inator");
    }

    @Test
    void perFileTokenEstimateIsPositive() throws IOException {
        write("src/Big.java", "public class Big { " + "int x = 1; ".repeat(200) + "}");

        ProcessingResult result = new SplitProcessor(config()).process();
        assertThat(result.getOutputFiles().get(0).getEstimatedTokens()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------

    private void write(String rel, String content) throws IOException {
        Path target = repoRoot.resolve(rel.replace('/', java.io.File.separatorChar));
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
