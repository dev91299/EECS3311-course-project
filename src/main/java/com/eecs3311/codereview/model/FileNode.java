package com.eecs3311.codereview.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A node in the imported repository tree: either a single file (leaf) or a directory
 * (composite) that contains further nodes.
 *
 * <p><strong>COMPOSITE PATTERN.</strong> This one class plays both the Leaf and the
 * Composite role, with {@code isDirectory} selecting the behaviour. Client code - the Swing
 * tree model, the CLI {@code tree} command, the agent's {@code list_files} tool and the
 * statistics in the report header - calls the same operations ({@link #getTotalFiles()},
 * {@link #getTotalSizeBytes()}, {@link #flatten()}, {@link #render}) on a single file and on
 * a whole project without distinguishing between them. Each operation recurses into children
 * when the node is a directory and returns its own value when it is a leaf.</p>
 *
 * <p>This pattern was <em>not</em> in the Stage 1 design, where {@code RepoManager.loadFiles()}
 * simply returned a flat {@code List}. A flat list could not express nesting, so the file
 * browser promised by feature F01 could not have been drawn from it. The change is recorded
 * in section 4 of the Stage 2 report.</p>
 */
public class FileNode {

    private final String name;
    private final String path;
    private final boolean directory;
    private final long sizeBytes;
    private final List<FileNode> children = new ArrayList<>();
    private FileNode parent;

    private FileNode(String name, String path, boolean directory, long sizeBytes) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.sizeBytes = sizeBytes;
    }

    /** Creates a leaf node representing one source file. */
    public static FileNode file(String name, String path, long sizeBytes) {
        return new FileNode(name, path, false, sizeBytes);
    }

    /** Creates a composite node representing a directory. */
    public static FileNode directory(String name, String path) {
        return new FileNode(name, path, true, 0L);
    }

    /**
     * Adds a child. Only meaningful on a directory; calling it on a file is ignored, which
     * keeps the uniform interface safe rather than throwing at the call site.
     */
    public FileNode add(FileNode child) {
        if (directory && child != null) {
            child.parent = this;
            children.add(child);
        }
        return this;
    }

    /** Children of a directory; always empty for a file. */
    public List<FileNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** Sorts children directories-first then alphabetically, recursively. */
    public void sortRecursively() {
        children.sort(Comparator.comparing((FileNode n) -> !n.directory)
                .thenComparing(n -> n.name.toLowerCase()));
        for (FileNode child : children) {
            child.sortRecursively();
        }
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public FileNode getParent() {
        return parent;
    }

    /** File extension in lower case without the dot, or an empty string for directories. */
    public String getExtension() {
        if (directory) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    // --------------------------------------------- uniform recursive operations

    /**
     * COMPOSITE operation: number of files at and below this node. A leaf answers 1,
     * a directory sums its children.
     */
    public int getTotalFiles() {
        if (!directory) {
            return 1;
        }
        int total = 0;
        for (FileNode child : children) {
            total += child.getTotalFiles();
        }
        return total;
    }

    /** COMPOSITE operation: number of directories at and below this node. */
    public int getTotalDirectories() {
        if (!directory) {
            return 0;
        }
        int total = 1;
        for (FileNode child : children) {
            total += child.getTotalDirectories();
        }
        return total;
    }

    /** COMPOSITE operation: total size in bytes at and below this node. */
    public long getTotalSizeBytes() {
        if (!directory) {
            return sizeBytes;
        }
        long total = 0L;
        for (FileNode child : children) {
            total += child.getTotalSizeBytes();
        }
        return total;
    }

    /** COMPOSITE operation: every file at and below this node, as a flat list. */
    public List<FileNode> flatten() {
        List<FileNode> result = new ArrayList<>();
        collect(result);
        return result;
    }

    private void collect(List<FileNode> sink) {
        if (!directory) {
            sink.add(this);
            return;
        }
        for (FileNode child : children) {
            child.collect(sink);
        }
    }

    /**
     * COMPOSITE operation: finds the first file whose path or name matches the query,
     * searching the whole subtree. This is how both views resolve a user-typed file name
     * such as {@code Calculator.java} to a full path.
     */
    public Optional<FileNode> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String needle = query.trim().replace('\\', '/');
        for (FileNode node : flatten()) {
            String normalisedPath = node.path.replace('\\', '/');
            if (normalisedPath.equals(needle)
                    || normalisedPath.endsWith("/" + needle)
                    || node.name.equalsIgnoreCase(needle)) {
                return Optional.of(node);
            }
        }
        for (FileNode node : flatten()) {
            if (node.path.replace('\\', '/').toLowerCase().contains(needle.toLowerCase())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /**
     * COMPOSITE operation: renders the subtree as indented text. The CLI prints this
     * directly; the GUI uses the node objects instead.
     *
     * @param maxDepth how deep to descend before stopping
     */
    public String render(int maxDepth) {
        StringBuilder sb = new StringBuilder();
        render(sb, "", true, 0, maxDepth);
        return sb.toString();
    }

    private void render(StringBuilder sb, String prefix, boolean isLast, int depth, int maxDepth) {
        sb.append(prefix);
        if (depth > 0) {
            sb.append(isLast ? "`-- " : "|-- ");
        }
        sb.append(name);
        if (directory) {
            sb.append("/  (").append(getTotalFiles()).append(" files)");
        } else {
            sb.append("  ").append(humanSize(sizeBytes));
        }
        sb.append('\n');
        if (!directory || depth >= maxDepth) {
            if (directory && !children.isEmpty()) {
                sb.append(prefix).append(depth > 0 ? (isLast ? "    " : "|   ") : "")
                  .append("... ").append(getTotalFiles()).append(" more files\n");
            }
            return;
        }
        String childPrefix = prefix + (depth > 0 ? (isLast ? "    " : "|   ") : "");
        for (int i = 0; i < children.size(); i++) {
            children.get(i).render(sb, childPrefix, i == children.size() - 1, depth + 1, maxDepth);
        }
    }

    /** Formats a byte count for display. */
    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return directory ? name + "/" : name;
    }
}
