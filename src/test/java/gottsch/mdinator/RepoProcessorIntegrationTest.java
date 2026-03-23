package gottsch.mdinator;

import gottsch.mdinator.core.RepoProcessor;
import gottsch.mdinator.model.ProcessingConfig;
import gottsch.mdinator.model.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepoProcessorIntegrationTest {

    @TempDir
    Path repoRoot;

    @Test
    void processesSingleJavaFile() throws IOException {
        // Given: a mini Java project
        Path srcDir = repoRoot.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("App.java"),
            "package com.example;\npublic class App { public static void main(String[] args) {} }\n");
        Files.writeString(repoRoot.resolve("build.gradle"),
            "plugins { id 'java' }\n");

        Path output = repoRoot.resolve("output.md");

        ProcessingConfig config = ProcessingConfig.builder()
            .repoPath(repoRoot)
            .includePatterns(List.of("**/*.java"))
            .excludePatterns(List.of())
            .outputPath(output)
            .includeToc(true)
            .includeTree(true)
            .maxTokens(200_000)
            .maxFileSizeKb(500)
            .stripComments(false)
            .verbose(false)
            .build();

        // When
        ProcessingResult result = new RepoProcessor(config).process();

        // Then
        assertThat(result.getIncludedFileCount()).isEqualTo(1);
        assertThat(output).exists();

        String md = Files.readString(output);
        assertThat(md).contains("# Repository Context");
        assertThat(md).contains("Table of Contents");
        assertThat(md).contains("Repository Structure");
        assertThat(md).contains("src/main/java/com/example/App.java");
        assertThat(md).contains("```java");
        assertThat(md).contains("public class App");
    }

    @Test
    void excludesPatternsCorrectly() throws IOException {
        Path mainDir = repoRoot.resolve("src/main/java");
        Path testDir = repoRoot.resolve("src/test/java");
        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);

        Files.writeString(mainDir.resolve("App.java"), "public class App {}");
        Files.writeString(testDir.resolve("AppTest.java"), "public class AppTest {}");

        Path output = repoRoot.resolve("output.md");

        ProcessingConfig config = ProcessingConfig.builder()
            .repoPath(repoRoot)
            .includePatterns(List.of("**/*.java"))
            .excludePatterns(List.of("**/test/**"))
            .outputPath(output)
            .includeToc(true)
            .includeTree(true)
            .maxTokens(200_000)
            .maxFileSizeKb(500)
            .stripComments(false)
            .verbose(false)
            .build();

        ProcessingResult result = new RepoProcessor(config).process();

        assertThat(result.getIncludedFileCount()).isEqualTo(1);
        String md = Files.readString(output);
        assertThat(md).contains("App.java");
        assertThat(md).doesNotContain("AppTest.java");
    }

    @Test
    void warnsWhenTokenLimitExceeded() throws IOException {
        Path src = repoRoot.resolve("src");
        Files.createDirectories(src);
        // Write a large-ish file
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("public class Line").append(i).append(" {}\n");
        Files.writeString(src.resolve("Big.java"), sb.toString());

        Path output = repoRoot.resolve("output.md");

        ProcessingConfig config = ProcessingConfig.builder()
            .repoPath(repoRoot)
            .includePatterns(List.of("**/*.java"))
            .excludePatterns(List.of())
            .outputPath(output)
            .includeToc(true)
            .includeTree(true)
            .maxTokens(100)   // tiny limit to trigger warning
            .maxFileSizeKb(500)
            .stripComments(false)
            .verbose(false)
            .build();

        ProcessingResult result = new RepoProcessor(config).process();
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings().get(0)).contains("token");
    }
}
