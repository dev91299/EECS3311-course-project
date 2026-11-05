package com.eecs3311.codereview.repo;

import com.eecs3311.codereview.model.FileNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Owns all file-system and Git access (features F01 and F08).
 *
 * <p>Separating this from the agent means the agent never touches the disk directly: it asks
 * for a file by path and receives text. That boundary is what lets the agent's tools be
 * sandboxed to the imported repository - {@link #readFile} refuses any path that escapes the
 * imported root, so a path such as {@code ../../etc/passwd} suggested by a model cannot be
 * read.</p>
 */
public class RepoManager {

    /** Extensions treated as reviewable source code. */
    private static final Set<String> SOURCE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "py", "js", "ts", "jsx", "tsx", "c", "h", "cpp", "hpp", "cc", "cs",
            "go", "rb", "rs", "kt", "kts", "swift", "scala", "php", "m", "sql", "sh",
            "html", "css", "xml", "json", "yml", "yaml", "md", "gradle", "properties"));

    /** Directories never walked, because they contain build output or dependencies. */
    private static final Set<String> IGNORED_DIRECTORIES = new HashSet<>(Arrays.asList(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "out", "bin",
            "dist", "__pycache__", ".gradle", ".mvn", "venv", ".venv", ".agent-memory"));

    private static final long MAX_FILE_BYTES = 1_000_000L;

    private Path rootPath;
    private FileNode rootNode;

    /**
     * Imports a repository from a local directory path.
     *
     * @param pathText the directory to import
     * @return the root of the file tree
     * @throws IOException when the path does not exist, is not a directory, or cannot be read
     */
    public FileNode loadFiles(String pathText) throws IOException {
        if (pathText == null || pathText.isBlank()) {
            throw new IOException("No repository path was given.");
        }
        Path path = Paths.get(pathText.trim()).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new IOException("That path does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IOException("That path is a file, not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IOException("Permission denied reading: " + path
                    + ". Check the folder's access permissions and try again.");
        }

        this.rootPath = path;
        FileNode root = FileNode.directory(path.getFileName() == null
                ? path.toString() : path.getFileName().toString(), path.toString());
        buildTree(path, root, 0);
        root.sortRecursively();
        this.rootNode = root;
        return root;
    }

    /**
     * Clones a remote repository into a temporary directory and imports it.
     * Requires the {@code git} command on the PATH.
     */
    public FileNode cloneAndLoad(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IOException("No repository URL was given.");
        }
        Path target = Files.createTempDirectory("code-review-clone-");
        ProcessResult result = runCommand(target.getParent(), 120,
                "git", "clone", "--depth", "50", url.trim(), target.toString());
        if (result.exitCode != 0) {
            throw new IOException("git clone failed: " + result.combinedOutput().trim());
        }
        return loadFiles(target.toString());
    }

    private void buildTree(Path directory, FileNode parent, int depth) throws IOException {
        if (depth > 12) {
            return;
        }
        List<Path> entries;
        try (Stream<Path> stream = Files.list(directory)) {
            entries = stream.collect(Collectors.toList());
        } catch (IOException e) {
            return; // an unreadable subdirectory must not abort the whole import
        }
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (Files.isDirectory(entry)) {
                if (IGNORED_DIRECTORIES.contains(name) || name.startsWith(".")) {
                    continue;
                }
                FileNode child = FileNode.directory(name, entry.toString());
                buildTree(entry, child, depth + 1);
                if (child.getTotalFiles() > 0) {
                    parent.add(child);
                }
            } else if (isSourceFile(name)) {
                long size;
                try {
                    size = Files.size(entry);
                } catch (IOException e) {
                    size = 0L;
                }
                if (size <= MAX_FILE_BYTES) {
                    parent.add(FileNode.file(name, entry.toString(), size));
                }
            }
        }
    }

    private boolean isSourceFile(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return SOURCE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    /**
     * Reads a file's text.
     *
     * <p>The path is resolved against the imported root and verified to stay inside it.
     * This is the sandbox boundary for the agent's tools.</p>
     */
    public String readFile(String filePath) throws IOException {
        Path resolved = resolveInsideRoot(filePath);
        if (!Files.exists(resolved)) {
            throw new IOException("File not found: " + filePath);
        }
        if (Files.size(resolved) > MAX_FILE_BYTES) {
            throw new IOException("File is too large to review: " + filePath);
        }
        return Files.readString(resolved, StandardCharsets.UTF_8);
    }

    /** Reads a file and returns an empty string instead of throwing; used by tools. */
    public String readFileQuietly(String filePath) {
        try {
            return readFile(filePath);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Resolves a path and rejects anything outside the imported repository. */
    private Path resolveInsideRoot(String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("No file path was given.");
        }
        Path candidate = Paths.get(filePath.trim());
        Path resolved = candidate.isAbsolute()
                ? candidate.normalize()
                : (rootPath == null ? candidate.toAbsolutePath().normalize()
                                    : rootPath.resolve(candidate).normalize());
        if (rootPath != null && !resolved.startsWith(rootPath)) {
            throw new IOException("Refused: '" + filePath
                    + "' is outside the imported repository.");
        }
        return resolved;
    }

    /** True when the imported directory is a Git working tree. */
    public boolean isGitRepository() {
        return rootPath != null && Files.isDirectory(rootPath.resolve(".git"));
    }

    /**
     * Returns the unified diff between two commits (feature F08).
     *
     * @param commitA the earlier reference
     * @param commitB the later reference
     * @throws IOException when the directory is not a Git repository or a reference is unknown
     */
    public String getDiff(String commitA, String commitB) throws IOException {
        requireGit();
        ProcessResult result = runCommand(rootPath, 30,
                "git", "diff", "--stat", commitA + ".." + commitB);
        if (result.exitCode != 0) {
            throw new IOException("Git Reference Error: " + result.combinedOutput().trim());
        }
        return result.stdout;
    }

    /** Returns the names of files that changed between two commits. */
    public List<String> getChangedFiles(String commitA, String commitB) throws IOException {
        requireGit();
        ProcessResult result = runCommand(rootPath, 30,
                "git", "diff", "--name-only", commitA + ".." + commitB);
        if (result.exitCode != 0) {
            throw new IOException("Git Reference Error: " + result.combinedOutput().trim());
        }
        return Arrays.stream(result.stdout.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Returns the content of one file as it existed at a given commit. */
    public String getFileAtCommit(String commit, String relativePath) throws IOException {
        requireGit();
        ProcessResult result = runCommand(rootPath, 30,
                "git", "show", commit + ":" + relativePath);
        if (result.exitCode != 0) {
            throw new IOException("Git Reference Error: cannot read " + relativePath
                    + " at " + commit);
        }
        return result.stdout;
    }

    /** Returns recent commits as "hash<TAB>subject" lines, for the commit picker. */
    public List<String> listCommits(int limit) throws IOException {
        requireGit();
        ProcessResult result = runCommand(rootPath, 30,
                "git", "log", "--pretty=format:%h\t%ad\t%s", "--date=short",
                "-n", String.valueOf(Math.max(1, limit)));
        if (result.exitCode != 0) {
            throw new IOException("Could not read the commit history: "
                    + result.combinedOutput().trim());
        }
        return Arrays.stream(result.stdout.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void requireGit() throws IOException {
        if (rootPath == null) {
            throw new IOException("No repository has been imported yet.");
        }
        if (!isGitRepository()) {
            throw new IOException("The imported folder is not a Git repository, so version "
                    + "comparison is unavailable. Import a folder that contains a .git directory.");
        }
    }

    /** Runs an external command and captures its output. */
    private ProcessResult runCommand(Path workingDirectory, int timeoutSeconds, String... command)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("Could not run '" + command[0]
                    + "'. Is it installed and on your PATH? (" + e.getMessage() + ")");
        }
        String stdout;
        String stderr;
        try {
            stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("The command timed out after " + timeoutSeconds + " seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The command was interrupted.");
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    /** The absolute path of the imported repository, or null when nothing is imported. */
    public Path getRootPath() {
        return rootPath;
    }

    /** The imported file tree, or null when nothing is imported. */
    public FileNode getRootNode() {
        return rootNode;
    }

    /** True when a repository has been imported. */
    public boolean isLoaded() {
        return rootNode != null;
    }

    /** Resolves a user-typed file name to a node in the tree. */
    public Optional<FileNode> findFile(String query) {
        return rootNode == null ? Optional.empty() : rootNode.find(query);
    }

    /** Every source file in the imported repository. */
    public List<FileNode> allFiles() {
        return rootNode == null ? new ArrayList<>() : rootNode.flatten();
    }

    /** Converts an absolute path to one relative to the repository root, for display. */
    public String relativise(String absolutePath) {
        if (rootPath == null || absolutePath == null) {
            return absolutePath == null ? "" : absolutePath;
        }
        try {
            return rootPath.relativize(Paths.get(absolutePath)).toString().replace('\\', '/');
        } catch (RuntimeException e) {
            return absolutePath;
        }
    }

    /** Reads the optional project rules file used by the coding-standard checker. */
    public String readRulesFile() {
        if (rootPath == null) {
            return "";
        }
        for (String candidate : new String[]{".rules", ".codereview-rules", "CONVENTIONS.md"}) {
            Path path = rootPath.resolve(candidate);
            if (Files.isReadable(path)) {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                    // fall through to the next candidate
                }
            }
        }
        return "";
    }

    /** Captured output of an external command. */
    private static final class ProcessResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        private String combinedOutput() {
            return (stdout + "\n" + stderr).trim();
        }
    }
}
