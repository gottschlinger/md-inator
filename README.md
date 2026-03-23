# md-inator

> Convert a local or remote Git repository into Markdown file(s)
> optimised for ingestion by Claude (and other LLMs).
>
> *Yes, yes it is.*

---

## Why?

When you paste code into Claude you lose structure — no file paths, no tree,
no sense of how things fit together. `md-inator` packages an entire codebase
into one (or more) Markdown documents with:

- A **metadata header** (repo name, timestamp, patterns used)
- A **Table of Contents** linking to every file section
- An **ASCII file-tree** showing the repo structure at a glance
- Each file in its own **language-tagged code fence** with a clear heading
- An estimated **token count** with a budget warning if you exceed a threshold

Works with **local repos**, **public GitHub repos**, and **private GitHub repos**.

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 17+     |
| Gradle | 7.6+ (or use the wrapper) |

---

## Build

```bash
cd md-inator

# Build the fat JAR
./gradlew jar

# Output: build/libs/md-inator-1.0.0.jar
```

---

## Usage

```
md-inator <repoPath|githubUrl|owner/repo> --include <glob> [options]
```

### Required arguments

| Argument | Description |
|----------|-------------|
| `<repoArg>` | Local path, `https://github.com/owner/repo`, or `owner/repo` shorthand |
| `-i / --include <glob>` | Glob pattern for files to include. **Repeatable.** |

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `-e / --exclude <glob>` | _(none)_ | Glob pattern(s) to exclude. Repeatable. |
| `-o / --output <file>` | `<repo-name>-context.md` | Output file path (single-file mode) |
| `--split` | false | Write one `.md` per leaf directory instead of one combined file |
| `--output-dir <dir>` | `<repo-name>-context/` | Output directory for `--split` mode |
| `--auto-chunk` | false | Split output into multiple `.md` files by token budget |
| `--chunk-tokens <n>` | 150000 | Token budget per chunk when `--auto-chunk` is enabled |
| `--branch <name>` | HEAD | Branch or tag to fetch (remote repos only) |
| `--token <pat>` | _(none)_ | GitHub Personal Access Token for private repos / higher rate limits |
| `--no-toc` | false | Omit the Table of Contents |
| `--no-tree` | false | Omit the ASCII file tree |
| `--max-tokens <n>` | 200000 | Warn if estimated token count exceeds this |
| `--max-file-kb <n>` | 500 | Skip individual files larger than this (KB) |
| `--strip-comments` | false | Remove `//` and `/* */` comments from C-style source files |
| `--dry-run` | false | List matched files and token estimate — no output written |
| `-v / --verbose` | false | Print each included/excluded file to stderr |
| `-h / --help` | | Show help |
| `-V / --version` | | Show version |

---

## Examples

### Local repo

```bash
# All Java files
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" --include "**/*.java"

# Java + config files, custom output name
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" \
  --include "**/*.gradle" \
  --include "**/*.yml" \
  --output myrepo-context.md

# Exclude tests and generated code
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" \
  --exclude "**/test/**" \
  --exclude "**/generated/**"

# Dry-run first — see what would be matched without writing output
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --dry-run

# Strip comments to reduce token count
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --strip-comments --max-tokens 150000
```

### Remote GitHub (public)

```bash
# Full URL
java -jar build/libs/md-inator-1.0.0.jar https://github.com/owner/repo \
  --include "**/*.java"

# Shorthand
java -jar build/libs/md-inator-1.0.0.jar owner/repo --include "**/*.java"

# Specific branch
java -jar build/libs/md-inator-1.0.0.jar owner/repo \
  --include "**/*.java" --branch develop
```

### Remote GitHub (private)

```bash
# With a Personal Access Token (PAT)
java -jar build/libs/md-inator-1.0.0.jar owner/private-repo \
  --include "**/*.java" --token ghp_yourtoken
```

### Split mode — one `.md` per directory

```bash
# Writes network.md, event.md, block.md etc. into ./myrepo-context/
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --split

# Custom output directory
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --split --output-dir "C:\Dev\context"
```

### Auto-chunking — split by token budget

```bash
# Default 150,000 token budget per chunk
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --auto-chunk

# Custom budget
java -jar build/libs/md-inator-1.0.0.jar "C:\Dev\myrepo" \
  --include "**/*.java" --auto-chunk --chunk-tokens 100000
```

Produces `myrepo-context-1.md`, `myrepo-context-2.md` etc.
Each file's header shows `Part N of M`.

---

## .mdinatorignore

Place a `.mdinatorignore` file in your repo root to exclude paths without
passing `--exclude` flags every run. Same syntax as `.gitignore`:

```
# ignore log files
**/*.log

# ignore generated code
**/generated/**

# ignore a specific directory
docs/internal/**
```

CLI `--exclude` flags take priority over `.mdinatorignore` patterns.

---

## Output format

```markdown
# Repository Context: `myrepo`

> **Generated by md-inator** · 2026-03-21 10:00:00 UTC
> | Property | Value |
> |---|---|
> | Repo path | `C:\Dev\myrepo` |
> | Include patterns | `**/*.java` |
> | Files included | 42 |

---

## Table of Contents

- [`src/main/java/com/example/App.java`](#src-main-java-com-example-app-java)

---

## Repository Structure

\```
myrepo/
├── src/
│   └── main/
│       └── java/
│           └── com/example/
│               └── App.java
\```

---

## Source Files

### `src/main/java/com/example/App.java`

> **Language:** java · **Size:** 1.2 KB

\```java
// file contents here
\```
```

---

## Default excludes

Always excluded regardless of your patterns:

```
.git/**          .gradle/**       .idea/**
.vscode/**       build/**         out/**
target/**        .mvn/**          node_modules/**
**/.DS_Store     **/Thumbs.db
```

---

## GitHub API rate limits

| Mode | Requests/hour |
|------|---------------|
| Unauthenticated | 60 |
| With `--token` (PAT) | 5,000 |

Each remote run costs 1 request for the file tree + 1 per matched file.
Use `--dry-run` first on unfamiliar repos to check file counts before committing.

---

## Running from IntelliJ

**Option A — Application Run Configuration (recommended):**
1. `Run > Edit Configurations > + > Application`
2. Main class: `com.mdinator.cli.MdinatorCli`
3. Program arguments: `"C:\path\to\repo" --include **/*.java`
4. Module: `md-inator.main`

**Option B — IntelliJ terminal (`Alt+F12`):**
```bash
java -jar build/libs/md-inator-1.0.0.jar "C:\path\to\repo" --include "**/*.java"
```

---

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success, no warnings |
| `1` | Fatal error (bad path, no files matched, I/O error) |
| `2` | Success with warnings (e.g. token budget exceeded) |

---

## Gradle tasks

```bash
./gradlew test     # Run all unit + integration tests
./gradlew jar      # Build fat JAR → build/libs/md-inator-1.0.0.jar
```

---

## v2.0 Roadmap

- [ ] GitLab integration (public + private repos)
- [ ] Exclude comments from all languages (Python `#`, Bash `#`, YAML `#`, HTML `<!-- -->`)
- [ ] `--split-strategy <leaf|every>` — choose between leaf-only and every-directory splitting
- [ ] `.mdinatorignore` support in remote GitHub mode
- [ ] `--since <commit>` — only include files changed since a given commit
- [ ] Watch mode — re-generate on file save

---

## License

MIT