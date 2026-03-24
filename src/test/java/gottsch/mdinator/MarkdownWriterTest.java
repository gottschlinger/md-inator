package gottsch.mdinator;

import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.SourceFile;
import gottsch.mdinator.output.MarkdownWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownWriterTest {

    @Test
    void anchorSimplePath() {
        assertThat(MarkdownWriter.toAnchor("src/main/App.java"))
            .isEqualTo("src-main-app-java");
    }

    @Test
    void anchorRootFile() {
        assertThat(MarkdownWriter.toAnchor("build.gradle"))
            .isEqualTo("build-gradle");
    }

    @Test
    void anchorNoLeadingOrTrailingHyphens() {
        String anchor = MarkdownWriter.toAnchor("src/main/App.java");
        assertThat(anchor).doesNotStartWith("-").doesNotEndWith("-");
    }

    @Test
    void duplicatePathsGetUniqueSuffixes() {
        // Two different paths that normalize to the same anchor
        assertThat(MarkdownWriter.toAnchor("src/main/app.java"))
                .isEqualTo(MarkdownWriter.toAnchor("src/main/APP.java"));
    }

    @Test
    void anchorAllocationDeduplicates() throws IOException {
        // Build two SourceFiles whose paths produce the same base anchor
        Path p1 = Path.of("src/main/app.java");
        Path p2 = Path.of("src/main/APP.java");
        SourceFile f1 = new SourceFile(p1, p1, "java", "class A {}", 10L);
        SourceFile f2 = new SourceFile(p2, p2, "java", "class B {}", 10L);

        ProcessingConfig config = ProcessingConfig.builder()
                .repoPath(Path.of("."))
                .includePatterns(List.of("**/*.java"))
                .excludePatterns(List.of())
                .outputPath(Path.of("out.md"))
                .includeToc(true).includeTree(false)
                .maxTokens(200_000).maxFileSizeKb(500)
                .stripComments(false).verbose(false)
                .build();

        StringWriter sw = new StringWriter();
        new MarkdownWriter(config).write(sw, List.of(f1, f2), "");
        String md = sw.toString();

        // TOC and section IDs should both appear, second one suffixed with -2
        assertThat(md).contains("#src-main-app-java)");
        assertThat(md).contains("#src-main-app-java-2)");
        assertThat(md).contains("<a id=\"src-main-app-java\">");
        assertThat(md).contains("<a id=\"src-main-app-java-2\">");
    }
}
