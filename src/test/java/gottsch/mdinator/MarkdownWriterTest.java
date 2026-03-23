package gottsch.mdinator;

import gottsch.mdinator.output.MarkdownWriter;
import org.junit.jupiter.api.Test;

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
}
