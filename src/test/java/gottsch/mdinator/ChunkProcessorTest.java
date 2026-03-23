package gottsch.mdinator;

import gottsch.mdinator.core.ChunkProcessor;
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

/**
 * Tests for {@link ChunkProcessor}.
 *
 * @author Mark Gottschling on March 21, 2026
 */
class ChunkProcessorTest {

    @TempDir Path repoRoot;
    @TempDir Path outputDir;

    private ProcessingConfig config(long chunkTokens) {
        return ProcessingConfig.builder()
                .repoPath(repoRoot)
                .includePatterns(List.of("**/*.java"))
                .excludePatterns(List.of())
                .outputPath(outputDir.resolve("repo-context.md"))
                .includeToc(true)
                .includeTree(false)
                .maxTokens(200_000)
                .maxFileSizeKb(500)
                .stripComments(false)
                .verbose(false)
                .build();
    }

    // --- basic chunking ---

    @Test
    void singleChunkWhenUnderBudget() throws IOException {
        write("src/A.java", repeat("class A {}", 10));
        write("src/B.java", repeat("class B {}", 10));

        ProcessingResult result = new ChunkProcessor(config(150_000), 150_000).process();

        assertThat(result.getOutputFiles()).hasSize(1);
        assertThat(result.getOutputFiles().get(0).getPath().getFileName().toString())
                .isEqualTo("repo-context-1.md");
    }

    @Test
    void splitsIntoMultipleChunksWhenOverBudget() throws IOException {
        // Each file is ~500 tokens, budget is 600 → each file gets its own chunk
        String content = repeat("x", 500 * 4); // ~500 tokens at 3.7 chars/token ~1850 chars
        write("src/A.java", content);
        write("src/B.java", content);
        write("src/C.java", content);

        ProcessingResult result = new ChunkProcessor(config(600), 600).process();

        assertThat(result.getOutputFiles().size()).isGreaterThan(1);
    }

    @Test
    void outputFilesHaveSequentialNames() throws IOException {
        String content = repeat("x", 2000); // ~540 tokens
        write("src/A.java", content);
        write("src/B.java", content);
        write("src/C.java", content);

        ProcessingResult result = new ChunkProcessor(config(600), 600).process();

        List<String> names = result.getOutputFiles().stream()
                .map(f -> f.getPath().getFileName().toString())
                .toList();
        for (int i = 0; i < names.size(); i++) {
            assertThat(names.get(i)).isEqualTo("repo-context-" + (i + 1) + ".md");
        }
    }

    @Test
    void eachChunkContainsHeader() throws IOException {
        String content = repeat("x", 2000);
        write("src/A.java", content);
        write("src/B.java", content);

        ProcessingResult result = new ChunkProcessor(config(600), 600).process();

        for (ProcessingResult.OutputFile of : result.getOutputFiles()) {
            String md = Files.readString(of.getPath());
            assertThat(md).contains("# Repository Context");
            assertThat(md).contains("Part");
            assertThat(md).contains("of");
        }
    }

    @Test
    void allFilesAccountedFor() throws IOException {
        write("src/A.java", repeat("x", 500));
        write("src/B.java", repeat("x", 500));
        write("src/C.java", repeat("x", 500));
        write("src/D.java", repeat("x", 500));
        write("src/E.java", repeat("x", 500));

        ProcessingResult result = new ChunkProcessor(config(1000), 1000).process();

        int totalFiles = result.getOutputFiles().stream()
                .mapToInt(ProcessingResult.OutputFile::getFileCount)
                .sum();
        assertThat(totalFiles).isEqualTo(result.getIncludedFileCount());
    }

    @Test
    void chunkLabelShowsPartNofM() throws IOException {
        String content = repeat("x", 2000);
        write("src/A.java", content);
        write("src/B.java", content);
        write("src/C.java", content);

        ProcessingResult result = new ChunkProcessor(config(600), 600).process();

        int total = result.getOutputFiles().size();
        for (int i = 0; i < total; i++) {
            assertThat(result.getOutputFiles().get(i).getDirectoryLabel())
                    .isEqualTo("Part " + (i + 1) + " of " + total);
        }
    }

    @Test
    void throwsWhenNoFilesMatch() {
        assertThatThrownBy(() -> new ChunkProcessor(config(150_000), 150_000).process())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No files matched");
    }

    @Test
    void totalIncludedCountMatchesAllFiles() throws IOException {
        write("src/A.java", "class A {}");
        write("src/B.java", "class B {}");
        write("src/C.java", "class C {}");

        ProcessingResult result = new ChunkProcessor(config(150_000), 150_000).process();
        assertThat(result.getIncludedFileCount()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------

    private void write(String rel, String content) throws IOException {
        Path target = repoRoot.resolve(rel.replace('/', java.io.File.separatorChar));
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static String repeat(String s, int times) {
        return s.repeat(times);
    }
}