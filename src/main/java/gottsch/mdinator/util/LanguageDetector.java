package gottsch.mdinator.util;

import java.nio.file.Path;
import java.util.Map;

/**
 * Maps file extensions to the language hint used in Markdown code fences.
 */
public final class LanguageDetector {

    private static final Map<String, String> EXT_MAP = Map.ofEntries(
        // JVM
        Map.entry("java",        "java"),
        Map.entry("kt",          "kotlin"),
        Map.entry("kts",         "kotlin"),
        Map.entry("groovy",      "groovy"),
        Map.entry("scala",       "scala"),
        Map.entry("clj",         "clojure"),
        // Web
        Map.entry("js",          "javascript"),
        Map.entry("mjs",         "javascript"),
        Map.entry("cjs",         "javascript"),
        Map.entry("ts",          "typescript"),
        Map.entry("tsx",         "tsx"),
        Map.entry("jsx",         "jsx"),
        Map.entry("html",        "html"),
        Map.entry("htm",         "html"),
        Map.entry("css",         "css"),
        Map.entry("scss",        "scss"),
        Map.entry("sass",        "sass"),
        Map.entry("less",        "less"),
        // Data / config
        Map.entry("json",        "json"),
        Map.entry("yaml",        "yaml"),
        Map.entry("yml",         "yaml"),
        Map.entry("toml",        "toml"),
        Map.entry("xml",         "xml"),
        Map.entry("properties",  "properties"),
        Map.entry("env",         "dotenv"),
        Map.entry("ini",         "ini"),
        Map.entry("conf",        "conf"),
        // Build
        Map.entry("gradle",      "groovy"),
        Map.entry("pom",         "xml"),
        Map.entry("mk",          "makefile"),
        Map.entry("makefile",    "makefile"),
        // Scripts
        Map.entry("sh",          "bash"),
        Map.entry("bash",        "bash"),
        Map.entry("zsh",         "bash"),
        Map.entry("fish",        "bash"),
        Map.entry("ps1",         "powershell"),
        Map.entry("bat",         "bat"),
        Map.entry("cmd",         "bat"),
        // Docs
        Map.entry("md",          "markdown"),
        Map.entry("mdx",         "mdx"),
        Map.entry("rst",         "rst"),
        Map.entry("adoc",        "asciidoc"),
        Map.entry("txt",         ""),
        // Systems
        Map.entry("c",           "c"),
        Map.entry("h",           "c"),
        Map.entry("cpp",         "cpp"),
        Map.entry("cc",          "cpp"),
        Map.entry("hpp",         "cpp"),
        Map.entry("rs",          "rust"),
        Map.entry("go",          "go"),
        Map.entry("py",          "python"),
        Map.entry("rb",          "ruby"),
        Map.entry("php",         "php"),
        Map.entry("swift",       "swift"),
        Map.entry("cs",          "csharp"),
        Map.entry("fs",          "fsharp"),
        Map.entry("ex",          "elixir"),
        Map.entry("exs",         "elixir"),
        Map.entry("erl",         "erlang"),
        Map.entry("hs",          "haskell"),
        Map.entry("lua",         "lua"),
        Map.entry("r",           "r"),
        Map.entry("sql",         "sql"),
        Map.entry("tf",          "hcl"),
        Map.entry("hcl",         "hcl"),
        Map.entry("proto",       "protobuf"),
        Map.entry("graphql",     "graphql"),
        Map.entry("gql",         "graphql"),
        Map.entry("dockerfile",  "dockerfile")
    );

    private LanguageDetector() {}

    /**
     * Returns the Markdown code-fence language hint for the given file.
     * Returns {@code "text"} for unknown extensions.
     */
    public static String detect(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        // Handle names like "Dockerfile", "Makefile" (no extension)
        if (EXT_MAP.containsKey(name)) return EXT_MAP.get(name);

        int dot = name.lastIndexOf('.');
        if (dot < 0) return "text";

        String ext = name.substring(dot + 1);
        return EXT_MAP.getOrDefault(ext, "text");
    }
}
