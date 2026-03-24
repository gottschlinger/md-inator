# Changelog

---

## [1.2.0] - 2026-03-23

### Changed
- `ChunkProcessor.partition()` now estimates tokens against the rendered Markdown
  per file (heading, code fence, separator) rather than raw source text, producing
  chunk boundaries that are much closer to the actual output size
- `CHUNK_OVERHEAD_TOKENS` bumped from 500 to 1,000 to account for per-chunk
  header, TOC, and file tree preamble
- `MarkdownWriter` anchor assignment now built up front in a single pass via
  `buildAnchorMap()` and shared between `writeToc()` and `writeFiles()`, so TOC
  links and section IDs always agree
- `--include` and `--exclude` CLI help text updated to document the implicit
  `**/` prefix behavior with examples
- `--strip-comments` CLI help text updated to document supported languages and
  known caveats around regex literals and template strings in JS/TS

### Added
- `MarkdownWriter.estimateRenderedTokens(SourceFile)` — static method used by
  `ChunkProcessor` to estimate per-file token cost without a full render pass
- `MarkdownWriter.buildAnchorMap(List<SourceFile>)` — builds a collision-free
  anchor assignment map for a document; duplicate base anchors get a `-2`, `-3`
  suffix
- New `GlobMatcherTest` cases covering `shouldDescend()` edge cases: directly
  excluded dirs, nested excluded dirs, `foo/**` vs `**/foo/**`, sibling dirs,
  consistency between `shouldDescend()` and `matches()`, and all default excludes
- New `MarkdownWriterTest` cases covering anchor collision detection and
  deduplication

### Fixed
- Duplicate TOC anchors when two file paths normalize to the same slug (e.g.
  differing only by case or punctuation) — second occurrence now gets `-2` suffix
- `ChunkProcessor` chunk boundaries were based on raw source size, causing chunks
  to exceed their token budget after Markdown rendering overhead was added

---

## [1.1.0] - 2026-03-23

### Changed
- Migrated build system from Gradle to Maven (`pom.xml` replaces `build.gradle`,
  `gradle.properties`, and `settings.gradle`)
- Fat JAR now produced by `maven-assembly-plugin` instead of Gradle's `application`
  plugin; output remains `target/md-inator-1.0.0.jar`
- Updated `md-inator.sh` wrapper to invoke `mvn package` instead of `./gradlew jar`
- Replaced brittle regex-based GitHub API JSON parsing in `GitHubFileCollector`
  with Jackson (`jackson-databind 2.17.0`)
- GitHub file fetching now uses the Blobs API (`/git/blobs/{sha}`) instead of the
  Contents API (`/contents/{path}`), returning raw text directly and supporting
  files up to 100 MB (up from 1 MB)

### Added
- Jackson `ObjectMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES=false` so
  new fields added by GitHub's API will not break parsing
- `GitHubApiClient.getBlob(owner, repo, sha)` replacing the removed
  `getFileContent(owner, repo, path, branch)`

### Removed
- `GitHubApiClient.getFileContent()` — superseded by `getBlob()`
- Regex patterns `TREE_ENTRY` and `TRUNCATED` in `GitHubFileCollector` —
  superseded by Jackson
- Gradle wrapper (`gradlew`, `gradlew.bat`), `build.gradle`, `settings.gradle`,
  and `gradle.properties`

### Fixed
- JSON parsing no longer breaks if GitHub changes whitespace or field ordering
  in API responses