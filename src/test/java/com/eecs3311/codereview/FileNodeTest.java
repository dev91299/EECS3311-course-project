package com.eecs3311.codereview;

import com.eecs3311.codereview.model.FileNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Composite pattern: the same operations must give correct answers whether they
 * are called on a leaf or on a directory containing an arbitrary tree.
 */
class FileNodeTest {

    private FileNode sampleTree() {
        FileNode root = FileNode.directory("project", "/project");
        FileNode src = FileNode.directory("src", "/project/src");
        FileNode main = FileNode.directory("main", "/project/src/main");

        main.add(FileNode.file("App.java", "/project/src/main/App.java", 1000));
        main.add(FileNode.file("Helper.java", "/project/src/main/Helper.java", 500));
        src.add(main);
        src.add(FileNode.file("README.md", "/project/src/README.md", 200));
        root.add(src);
        root.add(FileNode.file("pom.xml", "/project/pom.xml", 300));
        return root;
    }

    @Test
    void leafReportsItselfAsOneFile() {
        FileNode leaf = FileNode.file("App.java", "/project/App.java", 1234);
        assertEquals(1, leaf.getTotalFiles());
        assertEquals(0, leaf.getTotalDirectories());
        assertEquals(1234, leaf.getTotalSizeBytes());
        assertFalse(leaf.isDirectory());
    }

    @Test
    void compositeSumsItsEntireSubtree() {
        FileNode root = sampleTree();
        assertEquals(4, root.getTotalFiles(), "four files exist at all depths");
        assertEquals(3, root.getTotalDirectories(), "project, src and main");
        assertEquals(2000, root.getTotalSizeBytes(), "sizes sum across the whole tree");
    }

    @Test
    void addingAChildToALeafIsIgnored() {
        FileNode leaf = FileNode.file("App.java", "/project/App.java", 10);
        leaf.add(FileNode.file("Other.java", "/project/Other.java", 10));
        assertTrue(leaf.getChildren().isEmpty(), "a leaf cannot gain children");
        assertEquals(1, leaf.getTotalFiles());
    }

    @Test
    void flattenReturnsEveryFileAndNoDirectories() {
        assertEquals(4, sampleTree().flatten().size());
        assertTrue(sampleTree().flatten().stream().noneMatch(FileNode::isDirectory));
    }

    @Test
    void findLocatesByBareFileName() {
        Optional<FileNode> found = sampleTree().find("Helper.java");
        assertTrue(found.isPresent());
        assertEquals("/project/src/main/Helper.java", found.get().getPath());
    }

    @Test
    void findLocatesByPathSuffix() {
        Optional<FileNode> found = sampleTree().find("main/App.java");
        assertTrue(found.isPresent());
        assertEquals("App.java", found.get().getName());
    }

    @Test
    void findReturnsEmptyWhenNothingMatches() {
        assertTrue(sampleTree().find("DoesNotExist.java").isEmpty());
        assertTrue(sampleTree().find("").isEmpty());
    }

    @Test
    void renderShowsNestedStructure() {
        String rendered = sampleTree().render(3);
        assertTrue(rendered.contains("project/"));
        assertTrue(rendered.contains("App.java"));
        assertTrue(rendered.contains("src/"));
    }

    @Test
    void extensionIsLowerCasedAndEmptyForDirectories() {
        assertEquals("java", FileNode.file("App.JAVA", "/App.JAVA", 1).getExtension());
        assertEquals("", FileNode.directory("src", "/src").getExtension());
    }
}
