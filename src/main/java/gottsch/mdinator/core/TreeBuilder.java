package gottsch.mdinator.core;

import gottsch.mdinator.model.SourceFile;

import java.util.*;

/**
 * Builds an ASCII tree representation of the included files.
 *
 * <pre>
 * src/
 * ├── main/
 * │   └── java/
 * │       └── com/example/
 * │           ├── App.java
 * │           └── service/
 * │               └── UserService.java
 * └── test/
 *     └── java/
 *         └── com/example/
 *             └── AppTest.java
 * </pre>
 */
public final class TreeBuilder {

    private TreeBuilder() {}

    public static String build(List<SourceFile> files, String repoName) {
        // Build a virtual tree from relative path strings
        Node root = new Node(repoName, true);
        for (SourceFile f : files) {
            String[] parts = f.getRelativePathString().split("/");
            Node current = root;
            for (int i = 0; i < parts.length; i++) {
                boolean isFile = (i == parts.length - 1);
                current = current.getOrCreate(parts[i], !isFile);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("```\n");
        renderNode(root, "", true, sb);
        sb.append("```\n");
        return sb.toString();
    }

    private static void renderNode(Node node, String prefix, boolean isRoot, StringBuilder sb) {
        if (isRoot) {
            sb.append(node.name).append("/\n");
        }
        List<Node> children = new ArrayList<>(node.children.values());
        // Dirs first, then files
        children.sort(Comparator
            .comparing((Node n) -> n.isDir ? 0 : 1)
            .thenComparing(n -> n.name));

        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            boolean last = (i == children.size() - 1);
            String connector = last ? "└── " : "├── ";
            String childPrefix = prefix + (last ? "    " : "│   ");

            sb.append(prefix).append(connector).append(child.name);
            if (child.isDir) sb.append("/");
            sb.append("\n");

            if (child.isDir) {
                renderNode(child, childPrefix, false, sb);
            }
        }
    }

    // -------------------------------------------------------------------------

    private static final class Node {
        final String name;
        final boolean isDir;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name, boolean isDir) {
            this.name  = name;
            this.isDir = isDir;
        }

        Node getOrCreate(String childName, boolean dir) {
            return children.computeIfAbsent(childName, k -> new Node(k, dir));
        }
    }
}
