package com.eecs3311.codereview.ui;

import com.eecs3311.codereview.agent.AgentStep;
import com.eecs3311.codereview.app.AgentController;
import com.eecs3311.codereview.export.ExporterFactory;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.observer.Observer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The graphical user interface.
 *
 * <p><strong>OBSERVER PATTERN - concrete observer.</strong> The window registers itself with
 * the report and repaints in response to pushed events. It never polls and never blocks the
 * event thread waiting for the model: every call that reaches the network runs on a
 * {@link SwingWorker}, and the result arrives back on the event thread in {@code done()}.</p>
 *
 * <p><strong>COMMAND PATTERN - invoker.</strong> The Accept, Reject and Re-grade buttons
 * construct command objects through the controller rather than mutating findings directly,
 * which is what gives the window a working Undo button.</p>
 *
 * <p>Layout: a file tree on the left, the source viewer and findings table in the centre, and
 * a tabbed panel on the right holding the AI output, the chat assistant and the agent's live
 * reasoning trace.</p>
 */
public class CodeReviewGUI extends JFrame implements Observer {

    private static final long serialVersionUID = 1L;

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color PANEL_BG = new Color(0xF7, 0xF8, 0xFA);
    private static final Color ACCENT = new Color(0x2D, 0x6C, 0xDF);

    private final AgentController controller;

    private final JTree fileTree = new JTree(new DefaultMutableTreeNode("No repository"));
    private final JTextArea sourceArea = new JTextArea();
    private final FindingsTableModel findingsModel = new FindingsTableModel();
    private final JTable findingsTable = new JTable(findingsModel);
    private final JTextArea outputArea = new JTextArea();
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private final JTextArea traceArea = new JTextArea();
    private final JTabbedPane rightTabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JLabel countsLabel = new JLabel(" ");
    private final JLabel modeLabel = new JLabel(" ");
    private final JComboBox<String> strategyBox = new JComboBox<>();
    private final JComboBox<String> severityFilter =
            new JComboBox<>(new String[]{"All severities", "Critical", "High", "Medium", "Low", "Info"});

    private final JButton undoButton = new JButton("Undo");
    private final JButton redoButton = new JButton("Redo");
    private final JButton cancelAuditButton = new JButton("Stop audit");

    private String currentFilePath = "";

    public CodeReviewGUI(AgentController controller) {
        super("AI Code Review Agent  -  EECS3311 Stage 2");
        this.controller = controller;
        controller.addObserver(this);

        buildMenuBar();
        buildLayout();
        refreshStrategyList();
        updateModeBanner();
        updateUndoRedoButtons();

        // A repository may already be open when the window is created - for example when the
        // application was started with --repo. Observers only receive events published after
        // they register, so the initial state is pulled rather than waited for.
        if (controller.isRepositoryLoaded()) {
            populateTree(controller.getRepoManager().getRootNode());
            findingsModel.reload(controller.getReport().getSorted());
            updateCounts();
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(1360, 850));
        setMinimumSize(new Dimension(1050, 680));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ construction

    private void buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu repository = new JMenu("Repository");
        repository.add(menuItem("Open folder...", KeyEvent.VK_O, e -> actionImportFolder()));
        repository.add(menuItem("Open folder by path...", 0, e -> actionImportByPath()));
        repository.add(menuItem("Clone from Git URL...", 0, e -> actionCloneRepository()));
        repository.addSeparator();
        repository.add(menuItem("Exit", 0, e -> dispose()));
        bar.add(repository);

        JMenu review = new JMenu("Review");
        review.add(menuItem("Summarise file", 0, e -> actionSummarise()));
        review.add(menuItem("Analyse with selected strategy", KeyEvent.VK_R, e -> actionAnalyse()));
        review.add(menuItem("Full review (all strategies)", 0, e -> actionFullReview()));
        review.add(menuItem("Generate tests", KeyEvent.VK_T, e -> actionGenerateTests()));
        review.add(menuItem("Suggest refactoring", 0, e -> actionRefactor()));
        review.addSeparator();
        review.add(menuItem("Run autonomous audit...", KeyEvent.VK_U, e -> actionRunAudit()));
        bar.add(review);

        JMenu findings = new JMenu("Findings");
        findings.add(menuItem("Accept selected", KeyEvent.VK_A, e -> actionAccept()));
        findings.add(menuItem("Reject selected...", KeyEvent.VK_D, e -> actionReject()));
        findings.add(menuItem("Change severity...", 0, e -> actionChangeSeverity()));
        findings.add(menuItem("Dismiss all Info findings", 0, e -> actionBulkDismiss()));
        findings.addSeparator();
        findings.add(menuItem("Undo", KeyEvent.VK_Z, e -> actionUndo()));
        findings.add(menuItem("Redo", KeyEvent.VK_Y, e -> actionRedo()));
        findings.add(menuItem("Action history...", 0, e -> actionShowHistory()));
        findings.add(menuItem("Agent memory...", 0, e -> actionShowMemory()));
        bar.add(findings);

        JMenu versions = new JMenu("Versions");
        versions.add(menuItem("Save snapshot...", 0, e -> actionSnapshot()));
        versions.add(menuItem("Compare snapshots...", 0, e -> actionCompare()));
        versions.add(menuItem("Show recent commits", 0, e -> actionShowCommits()));
        bar.add(versions);

        JMenu reports = new JMenu("Report");
        reports.add(menuItem("Export report...", KeyEvent.VK_E, e -> actionExport()));
        reports.add(menuItem("Session usage and cost", 0, e -> actionShowUsage()));
        bar.add(reports);

        JMenu help = new JMenu("Help");
        help.add(menuItem("Configuration", 0, e -> actionShowConfiguration()));
        help.add(menuItem("About", 0, e -> actionAbout()));
        bar.add(help);

        setJMenuBar(bar);
    }

    private JMenuItem menuItem(String label, int acceleratorKey,
                               java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(label);
        if (acceleratorKey != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(acceleratorKey,
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        item.addActionListener(listener);
        return item;
    }

    private void buildLayout() {
        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);

        JSplitPane centre = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildSourcePanel(), buildFindingsPanel());
        centre.setResizeWeight(0.55);
        centre.setBorder(null);

        JSplitPane withTree = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildTreePanel(), centre);
        withTree.setResizeWeight(0.18);
        withTree.setBorder(null);

        JSplitPane full = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, withTree, buildRightPanel());
        full.setResizeWeight(0.66);
        full.setBorder(null);

        add(full, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        bar.add(toolButton("Open repository", e -> actionImportFolder()));
        bar.addSeparator();

        bar.add(new JLabel("Strategy: "));
        strategyBox.setMaximumSize(new Dimension(190, 28));
        strategyBox.setToolTipText("Which analysis to run (Strategy pattern)");
        bar.add(strategyBox);

        bar.add(toolButton("Analyse", e -> actionAnalyse()));
        bar.add(toolButton("Full review", e -> actionFullReview()));
        bar.add(toolButton("Summarise", e -> actionSummarise()));
        bar.add(toolButton("Generate tests", e -> actionGenerateTests()));
        bar.add(toolButton("Refactor", e -> actionRefactor()));
        bar.addSeparator();

        JButton audit = toolButton("Run autonomous audit", e -> actionRunAudit());
        audit.setBackground(ACCENT);
        audit.setForeground(Color.WHITE);
        audit.setOpaque(true);
        audit.setBorderPainted(false);
        audit.setToolTipText("The agent plans, selects tools and reviews the repository on its own");
        bar.add(audit);

        cancelAuditButton.addActionListener(e -> controller.cancelAudit());
        cancelAuditButton.setEnabled(false);
        bar.add(cancelAuditButton);

        bar.add(Box.createHorizontalGlue());

        undoButton.addActionListener(e -> actionUndo());
        redoButton.addActionListener(e -> actionRedo());
        bar.add(undoButton);
        bar.add(redoButton);
        bar.add(toolButton("Export report", e -> actionExport()));

        return bar;
    }

    private JButton toolButton(String label, java.awt.event.ActionListener listener) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.addActionListener(listener);
        return button;
    }

    private JPanel buildTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Repository  [F01]"));
        fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        fileTree.addTreeSelectionListener(this::onTreeSelection);
        panel.add(new JScrollPane(fileTree), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSourcePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Source"));
        sourceArea.setFont(MONO);
        sourceArea.setEditable(false);
        sourceArea.setTabSize(4);
        panel.add(new JScrollPane(sourceArea), BorderLayout.CENTER);

        JLabel hint = new JLabel("  Select text to review only that selection, or review the whole file.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        hint.setForeground(new Color(0x6B, 0x75, 0x80));
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFindingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Findings  [F03 / F06 / F07]"));

        findingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        findingsTable.setRowHeight(24);
        findingsTable.setAutoCreateRowSorter(true);
        findingsTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        findingsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        findingsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        findingsTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        findingsTable.getColumnModel().getColumn(4).setPreferredWidth(420);
        findingsTable.getColumnModel().getColumn(5).setPreferredWidth(85);
        findingsTable.setDefaultRenderer(Object.class, new SeverityCellRenderer());
        findingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedFindingDetail();
            }
        });

        panel.add(new JScrollPane(findingsTable), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        actions.add(new JLabel("Filter: "));
        severityFilter.addActionListener(e -> findingsModel.applyFilter(
                (String) severityFilter.getSelectedItem()));
        severityFilter.setMaximumSize(new Dimension(160, 28));
        actions.add(severityFilter);
        actions.add(Box.createHorizontalStrut(12));
        actions.add(toolButton("Accept", e -> actionAccept()));
        actions.add(toolButton("Reject", e -> actionReject()));
        actions.add(toolButton("Re-grade", e -> actionChangeSeverity()));
        actions.add(toolButton("Fix this", e -> actionRefactorSelectedFinding()));
        actions.add(Box.createHorizontalGlue());
        actions.add(countsLabel);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private JTabbedPane buildRightPanel() {
        outputArea.setFont(MONO);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setText("AI output appears here.\n\n"
                + "Open a repository, pick a file, then use Summarise, Analyse, Generate tests "
                + "or Refactor.");
        rightTabs.addTab("AI output", new JScrollPane(outputArea));

        rightTabs.addTab("Assistant  [F10]", buildChatPanel());

        traceArea.setFont(MONO);
        traceArea.setEditable(false);
        traceArea.setLineWrap(true);
        traceArea.setWrapStyleWord(true);
        traceArea.setText("The autonomous agent's reasoning appears here while it works.\n\n"
                + "Each step shows what the agent decided, which tool it chose, and what it "
                + "observed, so you can see how it reached its conclusions.");
        rightTabs.addTab("Agent trace  [F11]", new JScrollPane(traceArea));

        rightTabs.setPreferredSize(new Dimension(430, 600));
        return rightTabs;
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setText("Ask anything about the code you have open.\n\n"
                + "The assistant searches the repository for the parts relevant to your "
                + "question, so you can ask about files you have not opened.\n\n"
                + "Examples:\n"
                + "  Where is user input validated?\n"
                + "  Why might this method throw?\n"
                + "  What would break if I changed this signature?\n");
        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        chatInput.addActionListener(e -> actionSubmitChat());
        bottom.add(chatInput, BorderLayout.CENTER);
        bottom.add(toolButton("Send", e -> actionSubmitChat()), BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 10, 5, 10));
        bar.setBackground(PANEL_BG);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11.5f));
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 11.5f));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(modeLabel, BorderLayout.EAST);
        return bar;
    }

    private void refreshStrategyList() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        controller.getAgent().getStrategies().all()
                .forEach(strategy -> model.addElement(strategy.getName()));
        strategyBox.setModel(model);
    }

    private void updateModeBanner() {
        if (controller.isOfflineMode()) {
            modeLabel.setText("OFFLINE MODE - no API key; AI features limited");
            modeLabel.setForeground(new Color(0xB3, 0x1C, 0x1C));
        } else {
            modeLabel.setText(controller.describeConfiguration());
            modeLabel.setForeground(new Color(0x2E, 0x6D, 0x33));
        }
    }

    // ------------------------------------------------------------------ observer

    @Override
    public void updateUI(UpdateEvent event, Object payload) {
        SwingUtilities.invokeLater(() -> {
            switch (event) {
                case REPOSITORY_LOADED:
                    if (payload instanceof FileNode) {
                        populateTree((FileNode) payload);
                    }
                    break;
                case FINDINGS_ADDED:
                case ITEM_CHANGED:
                case REPORT_CLEARED:
                    findingsModel.reload(controller.getReport().getSorted());
                    updateCounts();
                    updateUndoRedoButtons();
                    break;
                case AGENT_STEP:
                    if (payload instanceof AgentStep) {
                        appendTrace((AgentStep) payload);
                    }
                    break;
                case TASK_STARTED:
                    statusLabel.setText(String.valueOf(payload));
                    break;
                case TASK_FINISHED:
                    statusLabel.setText(String.valueOf(payload));
                    break;
                case ERROR:
                    statusLabel.setText("Error: " + payload);
                    break;
                case EXPORT_COMPLETE:
                    statusLabel.setText("Report written to " + payload);
                    break;
                default:
                    break;
            }
        });
    }

    private void appendTrace(AgentStep step) {
        traceArea.append(step.renderFull() + "\n");
        traceArea.setCaretPosition(traceArea.getDocument().getLength());
        rightTabs.setSelectedIndex(2);
    }

    private void updateCounts() {
        Map<String, Integer> summary = controller.getReport().aggregateItems();
        countsLabel.setText(String.format("  %d findings - %d pending, %d accepted, %d rejected  ",
                summary.getOrDefault("total", 0), summary.getOrDefault("pending", 0),
                summary.getOrDefault("accepted", 0), summary.getOrDefault("rejected", 0)));
    }

    private void updateUndoRedoButtons() {
        undoButton.setEnabled(controller.getHistory().canUndo());
        redoButton.setEnabled(controller.getHistory().canRedo());
        undoButton.setToolTipText(controller.getHistory().canUndo()
                ? "Undo: " + controller.getHistory().peekUndoDescription() : "Nothing to undo");
        redoButton.setToolTipText(controller.getHistory().canRedo()
                ? "Redo: " + controller.getHistory().peekRedoDescription() : "Nothing to redo");
    }

    // ------------------------------------------------------------------ F01 actions

    private void actionImportFolder() {
        File chosen = chooseDirectory();
        if (chosen == null) {
            return;
        }
        importDirectory(chosen.getAbsolutePath());
    }

    /**
     * Asks the user for a directory.
     *
     * <p>Swing's {@link JFileChooser} in {@code DIRECTORIES_ONLY} mode is unreliable under
     * macOS's Aqua look and feel: it frequently presents an empty list, so there is nothing
     * to select and the Choose button stays disabled. macOS therefore gets the native
     * directory picker, which is what {@code apple.awt.fileDialogForDirectories} turns
     * {@link FileDialog} into. Every other platform uses {@code JFileChooser}, where
     * directory selection works correctly.</p>
     *
     * @return the chosen directory, or {@code null} when the user cancelled
     */
    private File chooseDirectory() {
        File start = defaultBrowseDirectory();

        if (isMacOs()) {
            String previous = System.getProperty("apple.awt.fileDialogForDirectories");
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            try {
                FileDialog dialog = new FileDialog(this, "Choose a project folder to review",
                        FileDialog.LOAD);
                if (start != null) {
                    dialog.setDirectory(start.getAbsolutePath());
                }
                dialog.setVisible(true);
                String directory = dialog.getDirectory();
                String name = dialog.getFile();
                if (name == null) {
                    return null;
                }
                return new File(directory, name);
            } finally {
                if (previous == null) {
                    System.clearProperty("apple.awt.fileDialogForDirectories");
                } else {
                    System.setProperty("apple.awt.fileDialogForDirectories", previous);
                }
            }
        }

        JFileChooser chooser = new JFileChooser(start);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a project folder to review");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile();
    }

    /**
     * Where the folder picker should open.
     *
     * <p>The bundled sample project is the thing a first-time user wants, so the picker
     * starts beside the application rather than in the home directory.</p>
     */
    private File defaultBrowseDirectory() {
        File samples = new File("samples");
        if (samples.isDirectory()) {
            return samples;
        }
        File working = new File(System.getProperty("user.dir", "."));
        return working.isDirectory() ? working : null;
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** Lets the user type or paste a path, for when a file dialog is unhelpful. */
    private void actionImportByPath() {
        String suggestion = new File("samples/demo-project").isDirectory()
                ? new File("samples/demo-project").getAbsolutePath()
                : System.getProperty("user.dir", "");

        String path = (String) JOptionPane.showInputDialog(this,
                "Full path of the project folder to review:\n\n"
                + "(You can drag a folder from Finder or File Explorer onto this box\n"
                + "to fill in its path.)",
                "Open folder by path  [F01]", JOptionPane.PLAIN_MESSAGE, null, null, suggestion);

        if (path == null || path.isBlank()) {
            return;
        }
        importDirectory(path.trim().replace("\\ ", " "));
    }

    /** Shared import path used by both ways of choosing a folder. */
    private void importDirectory(String path) {
        runInBackground("Importing repository...", () -> {
            controller.handleImport(path, false);
            return null;
        }, result -> statusLabel.setText("Repository imported."),
           error -> showError("Could not import the repository", error));
    }

    private void actionCloneRepository() {
        String url = JOptionPane.showInputDialog(this,
                "Git repository URL to clone:", "Clone repository", JOptionPane.PLAIN_MESSAGE);
        if (url == null || url.isBlank()) {
            return;
        }
        runInBackground("Cloning " + url + " ...", () -> {
            controller.handleImport(url, true);
            return null;
        }, result -> statusLabel.setText("Repository cloned and imported."),
           error -> showError("Could not clone the repository", error));
    }

    private void populateTree(FileNode root) {
        DefaultMutableTreeNode treeRoot = toTreeNode(root);
        fileTree.setModel(new DefaultTreeModel(treeRoot));
        fileTree.expandRow(0);
        statusLabel.setText("Imported " + root.getTotalFiles() + " files from " + root.getName());
    }

    private DefaultMutableTreeNode toTreeNode(FileNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (FileNode child : node.getChildren()) {
            treeNode.add(toTreeNode(child));
        }
        return treeNode;
    }

    private void onTreeSelection(TreeSelectionEvent event) {
        TreePath path = event.getNewLeadSelectionPath();
        if (path == null) {
            return;
        }
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(userObject instanceof FileNode)) {
            return;
        }
        FileNode node = (FileNode) userObject;
        if (node.isDirectory()) {
            return;
        }
        try {
            currentFilePath = node.getPath();
            sourceArea.setText(controller.readFile(currentFilePath));
            sourceArea.setCaretPosition(0);
            statusLabel.setText("Opened " + controller.getRepoManager().relativise(currentFilePath));
        } catch (IOException e) {
            showError("Could not open the file", e);
        }
    }

    // ------------------------------------------------------------------ analysis actions

    /** Returns the selected text, or the whole file when nothing is selected. */
    private String activeCode() {
        String selected = sourceArea.getSelectedText();
        return selected != null && !selected.isBlank() ? selected : sourceArea.getText();
    }

    private boolean requireFile() {
        if (currentFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Open a repository and select a file first.",
                    "No file selected", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    private void actionAnalyse() {
        if (!requireFile()) {
            return;
        }
        String strategy = (String) strategyBox.getSelectedItem();
        String code = activeCode();
        runInBackground("Analysing with the " + strategy + " strategy...",
                () -> controller.requestReview(currentFilePath, code, strategy),
                findings -> {
                    rightTabs.setSelectedIndex(0);
                    outputArea.setText(renderFindingsSummary(findings, strategy));
                    outputArea.setCaretPosition(0);
                },
                error -> showRetryableError("The analysis did not complete", error,
                        () -> actionAnalyse()));
    }

    private void actionFullReview() {
        if (!requireFile()) {
            return;
        }
        String code = activeCode();
        runInBackground("Running all strategies...",
                () -> controller.requestFullReview(currentFilePath, code),
                findings -> {
                    rightTabs.setSelectedIndex(0);
                    outputArea.setText(renderFindingsSummary(findings, "all strategies"));
                    outputArea.setCaretPosition(0);
                },
                error -> showRetryableError("The review did not complete", error,
                        () -> actionFullReview()));
    }

    private String renderFindingsSummary(List<ReviewItem> findings, String strategy) {
        StringBuilder sb = new StringBuilder();
        sb.append("ANALYSIS COMPLETE - ").append(strategy).append("\n\n");
        if (findings.isEmpty()) {
            sb.append("No issues were found in this pass.\n");
            return sb.toString();
        }
        sb.append(findings.size()).append(" finding(s). They are listed in the Findings table "
                + "below, where you can accept or reject each one.\n\n");
        for (ReviewItem finding : findings) {
            sb.append('[').append(finding.getSeverity().getLabel()).append("] line ")
              .append(finding.getLineNumber()).append(" - ").append(finding.getTitle()).append('\n')
              .append("    ").append(finding.getDescription()).append('\n');
            if (!finding.getSuggestion().isEmpty()) {
                sb.append("    Fix: ").append(finding.getSuggestion()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void actionSummarise() {
        if (!requireFile()) {
            return;
        }
        String code = activeCode();
        runInBackground("Summarising...",
                () -> controller.summarise(currentFilePath, code),
                summary -> {
                    rightTabs.setSelectedIndex(0);
                    outputArea.setText(summary);
                    outputArea.setCaretPosition(0);
                },
                error -> showRetryableError("The summary did not complete", error,
                        () -> actionSummarise()));
    }

    private void actionGenerateTests() {
        if (!requireFile()) {
            return;
        }
        String code = activeCode();
        runInBackground("Generating unit tests...",
                () -> controller.generateTests(currentFilePath, code),
                tests -> {
                    rightTabs.setSelectedIndex(0);
                    outputArea.setText(tests);
                    outputArea.setCaretPosition(0);
                },
                error -> showRetryableError("Test generation did not complete", error,
                        () -> actionGenerateTests()));
    }

    private void actionRefactor() {
        refactor(null);
    }

    private void actionRefactorSelectedFinding() {
        ReviewItem selected = selectedFinding();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a finding in the table first.",
                    "No finding selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        currentFilePath = selected.getFilePath();
        try {
            sourceArea.setText(controller.readFile(currentFilePath));
        } catch (IOException ignored) {
            // The viewer is a convenience; the refactoring can still proceed.
        }
        refactor(selected.getIssueId());
    }

    private void refactor(String issueId) {
        if (!requireFile()) {
            return;
        }
        String code = activeCode();
        runInBackground("Generating a refactoring...",
                () -> controller.suggestRefactor(currentFilePath, code, issueId),
                this::showRefactorDialog,
                error -> showRetryableError("The refactoring did not complete", error,
                        () -> refactor(issueId)));
    }

    /** Feature F04's side-by-side comparison view. */
    private void showRefactorDialog(AgentController.RefactorResult result) {
        JDialog dialog = new JDialog(this, "Suggested refactoring  [F04]", true);
        dialog.setSize(1180, 720);
        dialog.setLocationRelativeTo(this);

        JPanel columns = new JPanel(new GridLayout(1, 2, 8, 0));

        JTextArea original = new JTextArea(result.getOriginal());
        original.setFont(MONO);
        original.setEditable(false);
        JScrollPane left = new JScrollPane(original);
        left.setBorder(BorderFactory.createTitledBorder("Original"));

        JTextArea revised = new JTextArea(result.getRevised());
        revised.setFont(MONO);
        revised.setEditable(false);
        JScrollPane right = new JScrollPane(revised);
        right.setBorder(BorderFactory.createTitledBorder("Suggested  (+"
                + result.getLinesAdded() + " / -" + result.getLinesRemoved() + " lines)"));

        columns.add(left);
        columns.add(right);

        JTextArea explanation = new JTextArea(stripCodeBlock(result.getExplanation()));
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        JScrollPane explanationPane = new JScrollPane(explanation);
        explanationPane.setBorder(BorderFactory.createTitledBorder("Why these changes"));
        explanationPane.setPreferredSize(new Dimension(1180, 170));

        JPanel buttons = new JPanel();
        JButton copy = new JButton("Copy suggested code to clipboard");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(result.getRevised()), null);
            statusLabel.setText("Suggested code copied to the clipboard.");
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        buttons.add(copy);
        buttons.add(close);

        dialog.setLayout(new BorderLayout());
        dialog.add(columns, BorderLayout.CENTER);
        dialog.add(explanationPane, BorderLayout.NORTH);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private String stripCodeBlock(String reply) {
        int close = reply.lastIndexOf("```");
        return close >= 0 && close + 3 < reply.length() ? reply.substring(close + 3).trim() : reply;
    }

    // ------------------------------------------------------------------ F11

    private void actionRunAudit() {
        if (!controller.isRepositoryLoaded()) {
            JOptionPane.showMessageDialog(this, "Open a repository first.",
                    "No repository", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String goal = (String) JOptionPane.showInputDialog(this,
                "What should the agent do?\n\n"
                + "It will plan, choose its own tools, read the code it decides is relevant,\n"
                + "and record findings as it goes. Each step appears in the Agent trace tab.",
                "Autonomous audit  [F11]", JOptionPane.PLAIN_MESSAGE, null, null,
                "Find the most serious security problems in this repository");
        if (goal == null || goal.isBlank()) {
            return;
        }

        traceArea.setText("");
        rightTabs.setSelectedIndex(2);
        cancelAuditButton.setEnabled(true);

        runInBackground("Autonomous audit running...",
                () -> controller.runAudit(goal, 0, null),
                conclusion -> {
                    cancelAuditButton.setEnabled(false);
                    traceArea.append("\n=== AGENT REPORT ===\n\n" + conclusion + "\n");
                    traceArea.setCaretPosition(traceArea.getDocument().getLength());
                },
                error -> {
                    cancelAuditButton.setEnabled(false);
                    showError("The audit stopped", error);
                });
    }

    // ------------------------------------------------------------------ F10

    private void actionSubmitChat() {
        String question = chatInput.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        chatInput.setText("");
        chatArea.append("\n\nYOU: " + question + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());

        String context = sourceArea.getText();
        runInBackground("Thinking...",
                () -> controller.submitChat(question, context),
                answer -> {
                    chatArea.append("\nASSISTANT: " + answer + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                },
                error -> {
                    chatArea.append("\nASSISTANT: I could not answer that. "
                            + describe(error) + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
    }

    // ------------------------------------------------------------------ F07, F12

    private ReviewItem selectedFinding() {
        int viewRow = findingsTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return findingsModel.getItemAt(findingsTable.convertRowIndexToModel(viewRow));
    }

    private void showSelectedFindingDetail() {
        ReviewItem finding = selectedFinding();
        if (finding == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(finding.getSeverity().getLabel().toUpperCase()).append("  -  ")
          .append(finding.getTitle()).append("\n\n");
        sb.append(finding.getIssueId()).append("   ")
          .append(controller.getRepoManager().relativise(finding.getFilePath()))
          .append(':').append(finding.getLineNumber()).append('\n');
        sb.append("Category: ").append(finding.getCategory())
          .append("    Found by: ").append(finding.getProducedBy())
          .append("    Status: ").append(finding.getStatus().getLabel()).append("\n\n");
        sb.append("WHY THIS MATTERS\n").append(finding.getDescription()).append("\n\n");
        if (!finding.getSuggestion().isEmpty()) {
            sb.append("SUGGESTED FIX\n").append(finding.getSuggestion()).append("\n\n");
        }
        if (!finding.getFeedback().isEmpty()) {
            sb.append("YOUR COMMENT\n").append(finding.getFeedback()).append('\n');
        }
        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        rightTabs.setSelectedIndex(0);
        highlightFindingLine(finding);
    }

    /** Scrolls the source viewer to the line a finding refers to. */
    private void highlightFindingLine(ReviewItem finding) {
        if (finding.getLineNumber() <= 0) {
            return;
        }
        if (!finding.getFilePath().equals(currentFilePath)) {
            try {
                currentFilePath = finding.getFilePath();
                sourceArea.setText(controller.readFile(currentFilePath));
            } catch (IOException e) {
                return;
            }
        }
        try {
            int start = sourceArea.getLineStartOffset(
                    Math.max(0, Math.min(finding.getLineNumber() - 1,
                            sourceArea.getLineCount() - 1)));
            int end = sourceArea.getLineEndOffset(
                    Math.max(0, Math.min(finding.getLineNumber() - 1,
                            sourceArea.getLineCount() - 1)));
            sourceArea.setCaretPosition(start);
            sourceArea.select(start, end);
            sourceArea.requestFocusInWindow();
        } catch (javax.swing.text.BadLocationException ignored) {
            // The finding may reference a line outside a truncated selection.
        }
    }

    private void actionAccept() {
        ReviewItem finding = selectedFinding();
        if (finding == null) {
            JOptionPane.showMessageDialog(this, "Select a finding in the table first.",
                    "No finding selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String note = JOptionPane.showInputDialog(this,
                "Accepting: " + finding.getTitle() + "\n\nOptional note:",
                "Accept finding  [F07]", JOptionPane.PLAIN_MESSAGE);
        if (note == null) {
            return;
        }
        controller.acceptFinding(finding.getIssueId(), note);
        statusLabel.setText("Accepted " + finding.getIssueId() + ". Undo is available.");
    }

    private void actionReject() {
        ReviewItem finding = selectedFinding();
        if (finding == null) {
            JOptionPane.showMessageDialog(this, "Select a finding in the table first.",
                    "No finding selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String reason = JOptionPane.showInputDialog(this,
                "Rejecting: " + finding.getTitle()
                + "\n\nWhy is this not a problem here?\n"
                + "The agent remembers your reason and stops reporting this kind of finding.",
                "Reject finding  [F07]", JOptionPane.PLAIN_MESSAGE);
        if (reason == null) {
            return;
        }
        controller.rejectFinding(finding.getIssueId(), reason);
        statusLabel.setText("Rejected " + finding.getIssueId()
                + " and recorded the reason in agent memory.");
    }

    private void actionChangeSeverity() {
        ReviewItem finding = selectedFinding();
        if (finding == null) {
            JOptionPane.showMessageDialog(this, "Select a finding in the table first.",
                    "No finding selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object chosen = JOptionPane.showInputDialog(this,
                "New severity for " + finding.getIssueId() + ":",
                "Change severity", JOptionPane.PLAIN_MESSAGE, null,
                Severity.values(), finding.getSeverity());
        if (chosen instanceof Severity) {
            controller.changeSeverity(finding.getIssueId(), (Severity) chosen);
        }
    }

    private void actionBulkDismiss() {
        int count = controller.bulkRejectAtOrBelow(Severity.INFO,
                "Bulk dismissed: informational findings are not tracked on this project");
        JOptionPane.showMessageDialog(this,
                count == 0 ? "There are no pending Info findings."
                           : "Rejected " + count + " Info finding(s) as one undoable action.",
                "Bulk dismiss", JOptionPane.INFORMATION_MESSAGE);
    }

    private void actionUndo() {
        Optional<String> undone = controller.undo();
        statusLabel.setText(undone.map(d -> "Undone: " + d).orElse("Nothing to undo."));
        updateUndoRedoButtons();
    }

    private void actionRedo() {
        Optional<String> redone = controller.redo();
        statusLabel.setText(redone.map(d -> "Redone: " + d).orElse("Nothing to redo."));
        updateUndoRedoButtons();
    }

    private void actionShowHistory() {
        showTextDialog("Review action history  [F12]", controller.getHistory().renderLog());
    }

    private void actionShowMemory() {
        StringBuilder sb = new StringBuilder();
        var memory = controller.getAgent().getMemory();
        sb.append("The agent remembers ").append(memory.size())
          .append(" review decision(s) for this repository.\n");
        sb.append("Stored at: ").append(memory.getStorageLocation()).append("\n\n");
        if (memory.size() == 0) {
            sb.append("Reject a finding with a reason and it will be remembered here, then fed "
                    + "into future analyses so the same objection is not raised twice.");
        } else {
            memory.getAll().forEach(entry -> sb.append('[').append(entry.getDecision())
                    .append("] ").append(entry.getTitle())
                    .append(entry.getReason().isEmpty() ? "" : "\n      " + entry.getReason())
                    .append("\n\n"));
        }
        showTextDialog("Agent memory", sb.toString());
    }

    // ------------------------------------------------------------------ F08

    private void actionSnapshot() {
        String label = JOptionPane.showInputDialog(this,
                "Label for this snapshot of the current findings:\n"
                + "(for example 'before' or a commit hash)",
                "Save snapshot  [F08]", JOptionPane.PLAIN_MESSAGE);
        if (label == null || label.isBlank()) {
            return;
        }
        String commit = JOptionPane.showInputDialog(this,
                "Optional Git commit hash this snapshot corresponds to:", "");
        var snapshot = controller.takeSnapshot(label, commit == null ? "" : commit);
        JOptionPane.showMessageDialog(this,
                "Saved '" + label + "' with " + snapshot.size() + " finding(s).\n\n"
                + "Now check out the other version, re-run the analysis, save a second\n"
                + "snapshot, then use Versions > Compare snapshots.",
                "Snapshot saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void actionCompare() {
        List<String> labels = controller.getSnapshotLabels();
        if (labels.size() < 2) {
            JOptionPane.showMessageDialog(this,
                    "Save at least two snapshots before comparing.",
                    "Not enough snapshots", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object first = JOptionPane.showInputDialog(this, "Earlier snapshot:", "Compare",
                JOptionPane.PLAIN_MESSAGE, null, labels.toArray(), labels.get(0));
        Object second = JOptionPane.showInputDialog(this, "Later snapshot:", "Compare",
                JOptionPane.PLAIN_MESSAGE, null, labels.toArray(), labels.get(labels.size() - 1));
        if (first == null || second == null) {
            return;
        }
        runInBackground("Comparing snapshots...",
                () -> controller.compareSnapshots(String.valueOf(first), String.valueOf(second)),
                delta -> showTextDialog("Version comparison  [F08]", delta.render()),
                error -> showError("Could not compare the snapshots", error));
    }

    private void actionShowCommits() {
        try {
            List<String> commits = controller.listCommits(20);
            showTextDialog("Recent commits", String.join("\n", commits));
        } catch (IOException e) {
            showError("Could not read the commit history", e);
        }
    }

    // ------------------------------------------------------------------ F09

    private void actionExport() {
        if (controller.getReport().getTotalIssues() == 0) {
            int proceed = JOptionPane.showConfirmDialog(this,
                    "There are no findings yet. Export an empty report anyway?",
                    "Nothing to export", JOptionPane.YES_NO_OPTION);
            if (proceed != JOptionPane.YES_OPTION) {
                return;
            }
        }
        Object format = JOptionPane.showInputDialog(this, "Export format:", "Export report  [F09]",
                JOptionPane.PLAIN_MESSAGE, null,
                ExporterFactory.supportedFormats().toArray(), "pdf");
        if (format == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report");
        chooser.setSelectedFile(new java.io.File("code-review-report." + format));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String destination = chooser.getSelectedFile().getAbsolutePath();
        runInBackground("Writing the report...",
                () -> controller.exportReport(destination, String.valueOf(format)),
                path -> JOptionPane.showMessageDialog(this,
                        "Report written to:\n" + path, "Export complete",
                        JOptionPane.INFORMATION_MESSAGE),
                error -> showError("Export failed", error));
    }

    private void actionShowUsage() {
        showTextDialog("Model usage this session",
                LLMClient.getInstance().getUsageStats().render()
                + "\nProvider chain: " + LLMClient.getInstance().describeProvider());
    }

    private void actionShowConfiguration() {
        StringBuilder sb = new StringBuilder();
        sb.append(controller.describeConfiguration()).append("\n\n");
        sb.append("Indexed files for retrieval: ")
          .append(controller.getAgent().getRetriever().getIndexedFileCount()).append('\n');
        sb.append("Coding rules from: ")
          .append(controller.getAgent().getStandardsChecker().getRulesSource()).append('\n');
        sb.append("Agent tools: ")
          .append(String.join(", ", controller.getAgent().getTools().names())).append("\n\n");
        sb.append("To change the model, edit the .env file in the project folder and restart.\n");
        sb.append("Never put a real API key in a screenshot, a report or a commit.\n");
        showTextDialog("Configuration", sb.toString());
    }

    private void actionAbout() {
        JOptionPane.showMessageDialog(this,
                "AI Code Review Agent\nEECS3311 Course Project, Stage 2\n\n"
                + "An agent that plans, selects tools, retrieves code, reviews it and "
                + "remembers what you decided.\n\n"
                + "Design patterns implemented: Singleton, Strategy, Command, Observer, Facade,\n"
                + "Composite, Factory Method, Template Method, Decorator, Memento, Builder.",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ infrastructure

    /** A unit of work that may fail. */
    private interface Work<T> {
        T run() throws Exception;
    }

    /**
     * Runs work off the event thread and delivers the result back onto it.
     *
     * <p>Without this every model call would freeze the window for the duration of the
     * request, which for an autonomous audit can be a minute or more.</p>
     */
    private <T> void runInBackground(String statusMessage, Work<T> work,
                                     java.util.function.Consumer<T> onSuccess,
                                     java.util.function.Consumer<Exception> onFailure) {
        statusLabel.setText(statusMessage);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.run();
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    onSuccess.accept(get());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    onFailure.accept(cause instanceof Exception
                            ? (Exception) cause : new Exception(cause));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private String describe(Exception error) {
        if (error instanceof LLMException) {
            return ((LLMException) error).getUserMessage();
        }
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private void showError(String title, Exception error) {
        statusLabel.setText(title + ": " + describe(error));
        JOptionPane.showMessageDialog(this, describe(error), title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows a failure with a Retry button. This is the alternative flow from sequence
     * diagram SD01: after the automatic retries inside the provider chain are exhausted,
     * the user is offered an explicit retry rather than left with a dead end.
     */
    private void showRetryableError(String title, Exception error, Runnable retry) {
        statusLabel.setText(title + ": " + describe(error));
        int choice = JOptionPane.showOptionDialog(this,
                describe(error) + "\n\nWould you like to try again?",
                title, JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null,
                new Object[]{"Retry", "Cancel"}, "Retry");
        if (choice == JOptionPane.YES_OPTION) {
            retry.run();
        }
    }

    private void showTextDialog(String title, String content) {
        JTextArea area = new JTextArea(content);
        area.setFont(MONO);
        area.setEditable(false);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(880, 560));
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.PLAIN_MESSAGE);
    }

    // ------------------------------------------------------------------ table model

    /** Backs the findings table and applies the severity filter. */
    private static class FindingsTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS =
                {"ID", "Severity", "File", "Line", "Finding", "Status"};

        private List<ReviewItem> all = new ArrayList<>();
        private List<ReviewItem> visible = new ArrayList<>();
        private String filter = "All severities";

        void reload(List<ReviewItem> items) {
            this.all = new ArrayList<>(items);
            applyFilter(filter);
        }

        void applyFilter(String severityLabel) {
            this.filter = severityLabel == null ? "All severities" : severityLabel;
            if (filter.startsWith("All")) {
                visible = new ArrayList<>(all);
            } else {
                visible = new ArrayList<>();
                for (ReviewItem item : all) {
                    if (item.getSeverity().getLabel().equalsIgnoreCase(filter)) {
                        visible.add(item);
                    }
                }
            }
            fireTableDataChanged();
        }

        ReviewItem getItemAt(int row) {
            return row >= 0 && row < visible.size() ? visible.get(row) : null;
        }

        @Override
        public int getRowCount() {
            return visible.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ReviewItem item = visible.get(row);
            switch (column) {
                case 0: return item.getIssueId();
                case 1: return item.getSeverity().getLabel();
                case 2: return item.getFileName();
                case 3: return item.getLineNumber() == 0 ? "-" : item.getLineNumber();
                case 4: return item.getTitle();
                case 5: return item.getStatus().getLabel();
                default: return "";
            }
        }
    }

    /** Colours the severity column and dims findings that have already been triaged. */
    private static class SeverityCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            if (!selected) {
                component.setBackground(Color.WHITE);
                component.setForeground(Color.BLACK);
            }
            if (column == 1 && value != null) {
                Severity severity = Severity.fromText(String.valueOf(value));
                component.setForeground(selected ? Color.WHITE : severity.getColor());
                component.setFont(component.getFont().deriveFont(Font.BOLD));
            }
            Object status = table.getValueAt(row, 5);
            if (!selected && status != null && !"Pending".equals(status)) {
                component.setForeground(new Color(0x9A, 0xA2, 0xAC));
            }
            return component;
        }
    }

    // ------------------------------------------------------------------ launch

    /** Creates and shows the window on the event thread. */
    public static void launch(AgentController controller) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform look and feel is an acceptable fallback.
            }
            CodeReviewGUI window = new CodeReviewGUI(controller);
            window.setVisible(true);
            if (controller.isOfflineMode()) {
                JOptionPane.showMessageDialog(window,
                        "No LLM API key is configured, so the application is running in\n"
                        + "OFFLINE MODE. The AI features fall back to a limited static\n"
                        + "analyser and will not demonstrate the real agent.\n\n"
                        + "To enable the full system:\n"
                        + "  1. Copy .env.example to .env\n"
                        + "  2. Put your free API key in it (see the README)\n"
                        + "  3. Restart the application",
                        "Offline mode", JOptionPane.WARNING_MESSAGE);
            }
        });
    }
}
