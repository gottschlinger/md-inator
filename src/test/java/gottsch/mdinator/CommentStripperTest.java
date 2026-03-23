package gottsch.mdinator;

import gottsch.mdinator.util.CommentStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentStripperTest {

    @Test
    void stripsLineComments() {
        String src = "int x = 1; // this is a comment\nint y = 2;\n";
        String result = CommentStripper.strip(src, "java");
        assertThat(result).contains("int x = 1;").doesNotContain("// this is a comment");
        assertThat(result).contains("int y = 2;");
    }

    @Test
    void stripsBlockComments() {
        String src = "/* block comment */\npublic class App {}\n";
        String result = CommentStripper.strip(src, "java");
        assertThat(result).doesNotContain("block comment");
        assertThat(result).contains("public class App {}");
    }

    @Test
    void preservesStringLiterals() {
        String src = "String s = \"http://example.com\"; // url\n";
        String result = CommentStripper.strip(src, "java");
        assertThat(result).contains("\"http://example.com\"");
        assertThat(result).doesNotContain("// url");
    }

    @Test
    void stripsJavadocBlock() {
        String src = "/**\n * Javadoc\n */\npublic void foo() {}\n";
        String result = CommentStripper.strip(src, "java");
        assertThat(result).doesNotContain("Javadoc");
        assertThat(result).contains("public void foo()");
    }

    @Test
    void noOpForUnsupportedLanguage() {
        String src = "# Python comment\nprint('hello')\n";
        String result = CommentStripper.strip(src, "python");
        assertThat(result).isEqualTo(src);
    }

    @Test
    void noOpForYaml() {
        String src = "# yaml comment\nkey: value\n";
        assertThat(CommentStripper.strip(src, "yaml")).isEqualTo(src);
    }
}
