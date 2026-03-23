package gottsch.mdinator;

import gottsch.mdinator.util.LanguageDetector;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    @Test
    void detectsJava()       { assertThat(LanguageDetector.detect(Path.of("App.java"))).isEqualTo("java"); }

    @Test
    void detectsKotlin()     { assertThat(LanguageDetector.detect(Path.of("Main.kt"))).isEqualTo("kotlin"); }

    @Test
    void detectsGradle()     { assertThat(LanguageDetector.detect(Path.of("build.gradle"))).isEqualTo("groovy"); }

    @Test
    void detectsYaml()       { assertThat(LanguageDetector.detect(Path.of("config.yml"))).isEqualTo("yaml"); }

    @Test
    void detectsYamlLong()   { assertThat(LanguageDetector.detect(Path.of("config.yaml"))).isEqualTo("yaml"); }

    @Test
    void detectsDockerfile() { assertThat(LanguageDetector.detect(Path.of("Dockerfile"))).isEqualTo("dockerfile"); }

    @Test
    void unknownExtension()  { assertThat(LanguageDetector.detect(Path.of("mystery.xyz"))).isEqualTo("text"); }

    @Test
    void noExtension()       { assertThat(LanguageDetector.detect(Path.of("Makefile"))).isEqualTo("makefile"); }

    @Test
    void caseInsensitive()   { assertThat(LanguageDetector.detect(Path.of("App.JAVA"))).isEqualTo("java"); }

    @Test
    void detectsXml()        { assertThat(LanguageDetector.detect(Path.of("pom.xml"))).isEqualTo("xml"); }

    @Test
    void detectsSql()        { assertThat(LanguageDetector.detect(Path.of("schema.sql"))).isEqualTo("sql"); }
}
