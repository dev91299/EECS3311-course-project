package com.eecs3311.codereview.ui;

import com.eecs3311.codereview.agent.AgentMemory;
import com.eecs3311.codereview.agent.AgentStep;
import com.eecs3311.codereview.app.AgentController;
import com.eecs3311.codereview.export.ExporterFactory;
import com.eecs3311.codereview.llm.LLMClient;
import com.eecs3311.codereview.llm.LLMException;
import com.eecs3311.codereview.model.DeltaReport;
import com.eecs3311.codereview.model.FileNode;
import com.eecs3311.codereview.model.ReviewItem;
import com.eecs3311.codereview.model.ReviewStatus;
import com.eecs3311.codereview.model.Severity;
import com.eecs3311.codereview.observer.Observer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The command-line interface.
 *
 * <p><strong>OBSERVER PATTERN - concrete observer.</strong> Registers with the report and
 * prints progress as it arrives, which is what lets the user watch the agent reason during a
 * long audit instead of staring at a frozen prompt.</p>
 *
 * <p>Every feature reachable in the GUI is reachable here, because both interfaces call the
 * same {@link AgentController}. The CLI additionally supports one-shot mode
 * ({@code --command "..."}) so the tool can be scripted in a build pipeline.</p>
 */
public class CLIView implements Observer {

    private final AgentController controller;
    private final BufferedReader input;
    private String currentPath = "";
    private String currentCode = "";
    private boolean running = true;
    private boolean quiet;

    public CLIView(AgentController controller) {
        this.controller = controller;
        this.input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        controller.addObserver(this);
    }

    // ------------------------------------------------------------------ observer

    @Override
    public void updateUI(UpdateEvent event, Object payload) {
        if (quiet) {
            return;
        }
        switch (event) {
            case TASK_STARTED:
                println(Ansi.DIM + "  ... " + payload + Ansi.RESET);
                break;
            case TASK_FINISHED:
                println(Ansi.GREEN + "  ok  " + payload + Ansi.RESET);
                break;
            case ERROR:
                println(Ansi.RED + "  !!  " + payload + Ansi.RESET);
                break;
            case AGENT_STEP:
                if (payload instanceof AgentStep) {
                    printAgentStep((AgentStep) payload);
                }
                break;
            case EXPORT_COMPLETE:
                println(Ansi.GREEN + "  ok  Report written to " + payload + Ansi.RESET);
                break;
            default:
                break;
        }
    }

    private void printAgentStep(AgentStep step) {
        String colour;
        switch (step.getType()) {
            case PLAN:        colour = Ansi.CYAN; break;
            case THOUGHT:     colour = Ansi.DIM; break;
            case ACTION:      colour = Ansi.YELLOW; break;
            case OBSERVATION: colour = Ansi.RESET; break;
            case CONCLUSION:  colour = Ansi.GREEN; break;
            default:          colour = Ansi.RED;
        }
        println(colour + step.renderLine() + Ansi.RESET);
    }

    // ------------------------------------------------------------------ entry points

    /** Runs the interactive loop. */
    public void start() {
        printBanner();
        while (running) {
            System.out.print(Ansi.BOLD + prompt() + Ansi.RESET);
            System.out.flush();
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                break;
            }
            if (line == null) {
                break;
            }
            try {
                handleCommand(line.trim());
            } catch (RuntimeException e) {
                println(Ansi.RED + "Error: " + e.getMessage() + Ansi.RESET);
            }
        }
        println("Goodbye.");
    }

    /**
     * Runs a single command and exits, for scripting.
     *
     * @return process exit code: 0 on success, 1 on failure
     */
    public int runOnce(String repositoryPath, String command) {
        this.quiet = false;
        try {
            if (repositoryPath != null && !repositoryPath.isBlank()) {
                controller.handleImport(repositoryPath, false);
            }
            handleCommand(command);
            return 0;
        } catch (IOException | RuntimeException e) {
            println(Ansi.RED + "Error: " + e.getMessage() + Ansi.RESET);
            return 1;
        }
    }

    private String prompt() {
        String repo = controller.isRepositoryLoaded()
                ? controller.getRepoManager().getRootPath().getFileName().toString()
                : "no-repo";
        String file = currentPath.isEmpty() ? "" : " " + shortName(currentPath);
        return "\ncodereview(" + repo + file + ")> ";
    }

    private void printBanner() {
        println("");
        println(Ansi.BOLD + "  AI Code Review Agent" + Ansi.RESET + "  -  EECS3311 Course Project, Stage 2");
        println("  Command-line interface. Type " + Ansi.BOLD + "help" + Ansi.RESET + " for commands.");
        println("");
        if (controller.isOfflineMode()) {
            println(Ansi.YELLOW
                    + "  OFFLINE MODE: no LLM API key is configured, so AI features fall back to a\n"
                    + "  limited static analyser. Add a key to .env and restart for the real agent.\n"
                    + "  See the README section 'Local configuration'." + Ansi.RESET);
        } else {
            println(Ansi.DIM + "  " + controller.describeConfiguration() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ dispatch

    private void handleCommand(String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "help":     case "?":  printHelp();                 break;
            case "open":     case "import": commandOpen(argument);   break;
            case "tree":     commandTree(argument);                  break;
            case "files":    commandFiles(argument);                 break;
            case "cat":      case "view": commandCat(argument);      break;
            case "analyze":  case "analyse": commandAnalyze(argument); break;
            case "review":   commandFullReview(argument);            break;
            case "summarize": case "summarise": commandSummarise(argument); break;
            case "tests":    commandTests(argument);                 break;
            case "refactor": commandRefactor(argument);              break;
            case "issues":   case "findings": commandIssues(argument); break;
            case "show":     commandShow(argument);                  break;
            case "accept":   commandAccept(argument);                break;
            case "reject":   commandReject(argument);                break;
            case "severity": commandSeverity(argument);              break;
            case "dismissall": commandBulkReject(argument);          break;
            case "undo":     commandUndo();                          break;
            case "redo":     commandRedo();                          break;
            case "history":  println(controller.getHistory().renderLog()); break;
            case "ask":      case "chat": commandAsk(argument);      break;
            case "audit":    commandAudit(argument);                 break;
            case "search":   commandSearch(argument);                break;
            case "snapshot": commandSnapshot(argument);              break;
            case "compare":  commandCompare(argument);               break;
            case "commits":  commandCommits();                       break;
            case "export":   commandExport(argument);                break;
            case "memory":   commandMemory(argument);                break;
            case "usage":    println(LLMClient.getInstance().getUsageStats().render()); break;
            case "config":   commandConfig();                        break;
            case "reload":   LLMClient.getInstance().reloadConfiguration();
                             println("Configuration reloaded. " + controller.describeConfiguration());
                             break;
            case "strategies": println(controller.getAgent().getStrategies().describe()); break;
            case "clear":    commandClear();                         break;
            case "exit":     case "quit": running = false;           break;
            default:
                printUnknownCommand(command, line);
        }
    }

    // ------------------------------------------------------------------ F01

    private void commandOpen(String argument) {
        if (argument.isEmpty()) {
            println("Usage: open <folder path>   or   open --clone <git url>");
            return;
        }
        boolean clone = argument.startsWith("--clone");
        String target = clone ? argument.substring(7).trim() : argument;
        try {
            FileNode root = controller.handleImport(target, clone);
            println("Imported " + Ansi.BOLD + root.getName() + Ansi.RESET
                    + ": " + root.getTotalFiles() + " source files, "
                    + root.getTotalDirectories() + " directories, "
                    + FileNode.humanSize(root.getTotalSizeBytes()) + ".");
            println(root.render(2));
        } catch (IOException e) {
            println(Ansi.RED + "Could not import: " + e.getMessage() + Ansi.RESET);
            println("Check the path exists and that you have permission to read it.");
        }
    }

    private void commandTree(String argument) {
        if (requireRepository()) {
            return;
        }
        int depth = 3;
        try {
            if (!argument.isEmpty()) {
                depth = Integer.parseInt(argument.trim());
            }
        } catch (NumberFormatException ignored) {
            println("Depth must be a number; using 3.");
        }
        println(controller.getRepoManager().getRootNode().render(depth));
    }

    private void commandFiles(String argument) {
        if (requireRepository()) {
            return;
        }
        List<FileNode> files = controller.getRepoManager().allFiles();
        int shown = 0;
        for (FileNode file : files) {
            String relative = controller.getRepoManager().relativise(file.getPath());
            if (argument.isEmpty() || relative.toLowerCase().contains(argument.toLowerCase())) {
                println("  " + relative + "  " + FileNode.humanSize(file.getTotalSizeBytes()));
                shown++;
            }
        }
        println(shown + " file(s) listed.");
    }

    private void commandCat(String argument) {
        if (requireRepository()) {
            return;
        }
        if (argument.isEmpty()) {
            println("Usage: cat <file>");
            return;
        }
        Optional<FileNode> node = controller.getRepoManager().findFile(argument);
        if (node.isEmpty()) {
            println("No file matching '" + argument + "'. Try: files " + argument);
            return;
        }
        try {
            currentPath = node.get().getPath();
            currentCode = controller.readFile(currentPath);
            String[] lines = currentCode.split("\n", -1);
            println(Ansi.BOLD + controller.getRepoManager().relativise(currentPath)
                    + Ansi.RESET + "  (" + lines.length + " lines)");
            for (int i = 0; i < Math.min(lines.length, 200); i++) {
                println(String.format(Ansi.DIM + "%4d" + Ansi.RESET + "  %s", i + 1, lines[i]));
            }
            if (lines.length > 200) {
                println(Ansi.DIM + "  ... " + (lines.length - 200)
                        + " more lines not shown." + Ansi.RESET);
            }
            println("This file is now the active selection for analyze, summarize, tests and refactor.");
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F03, F06

    private void commandAnalyze(String argument) {
        if (requireRepository()) {
            return;
        }
        String file = currentPath;
        String strategy = "security";

        if (!argument.isEmpty()) {
            String[] parts = argument.split("\\s+");
            Optional<FileNode> node = controller.getRepoManager().findFile(parts[0]);
            if (node.isPresent()) {
                file = node.get().getPath();
                if (parts.length > 1) {
                    strategy = parts[1].toLowerCase();
                }
            } else {
                strategy = parts[0].toLowerCase();
            }
        }
        if (file == null || file.isEmpty()) {
            println("No file selected. Use: analyze <file> [strategy], or select one with cat <file>.");
            return;
        }
        if (controller.getAgent().getStrategies().find(strategy).isEmpty()) {
            println("Unknown strategy '" + strategy + "'. Available:");
            println(controller.getAgent().getStrategies().describe());
            return;
        }
        try {
            currentPath = file;
            List<ReviewItem> findings = controller.requestReview(file, null, strategy);
            printFindings(findings);
        } catch (LLMException e) {
            println(Ansi.RED + "Analysis failed: " + e.getUserMessage() + Ansi.RESET);
            println("Type " + Ansi.BOLD + "analyze" + Ansi.RESET + " again to retry.");
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    private void commandFullReview(String argument) {
        if (requireRepository()) {
            return;
        }
        String file = resolveTarget(argument);
        if (file == null) {
            return;
        }
        try {
            currentPath = file;
            printFindings(controller.requestFullReview(file, null));
        } catch (LLMException e) {
            println(Ansi.RED + "Review failed: " + e.getUserMessage() + Ansi.RESET);
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F02

    private void commandSummarise(String argument) {
        if (requireRepository()) {
            return;
        }
        String file = resolveTarget(argument);
        if (file == null) {
            return;
        }
        try {
            currentPath = file;
            println("");
            println(controller.summarise(file, null));
        } catch (LLMException e) {
            println(Ansi.RED + "Summarisation failed: " + e.getUserMessage() + Ansi.RESET);
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F05

    private void commandTests(String argument) {
        if (requireRepository()) {
            return;
        }
        String file = resolveTarget(argument);
        if (file == null) {
            return;
        }
        try {
            currentPath = file;
            println("");
            println(controller.generateTests(file, null));
        } catch (LLMException e) {
            println(Ansi.RED + "Test generation failed: " + e.getUserMessage() + Ansi.RESET);
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F04

    private void commandRefactor(String argument) {
        if (requireRepository()) {
            return;
        }
        String issueId = null;
        String target = argument;
        if (argument.toUpperCase().startsWith("ISS-")) {
            issueId = argument.trim();
            Optional<ReviewItem> item = controller.getReport().findById(issueId);
            if (item.isEmpty()) {
                println("No finding with id " + issueId + ". Use: issues");
                return;
            }
            target = item.get().getFilePath();
        }
        String file = resolveTarget(target);
        if (file == null) {
            return;
        }
        try {
            currentPath = file;
            AgentController.RefactorResult result = controller.suggestRefactor(file, null, issueId);
            println("");
            println(Ansi.BOLD + "SIDE-BY-SIDE COMPARISON" + Ansi.RESET
                    + "  (+" + result.getLinesAdded() + " / -" + result.getLinesRemoved() + " lines)");
            println(result.getSideBySide());
            println(Ansi.BOLD + "EXPLANATION" + Ansi.RESET);
            println(stripCodeBlock(result.getExplanation()));
        } catch (LLMException e) {
            println(Ansi.RED + "Refactoring failed: " + e.getUserMessage() + Ansi.RESET);
        } catch (IOException e) {
            println(Ansi.RED + "Could not read the file: " + e.getMessage() + Ansi.RESET);
        }
    }

    private String stripCodeBlock(String reply) {
        int close = reply.lastIndexOf("```");
        return close >= 0 && close + 3 < reply.length() ? reply.substring(close + 3).trim() : reply;
    }

    // ------------------------------------------------------------------ findings display

    private void commandIssues(String argument) {
        List<ReviewItem> items = controller.getReport().getSorted();
        if (!argument.isEmpty()) {
            String filter = argument.toLowerCase();
            List<ReviewItem> filtered = new ArrayList<>();
            for (ReviewItem item : items) {
                if (item.getSeverity().name().toLowerCase().startsWith(filter)
                        || item.getStatus().name().toLowerCase().startsWith(filter)
                        || item.getFileName().toLowerCase().contains(filter)) {
                    filtered.add(item);
                }
            }
            items = filtered;
        }
        if (items.isEmpty()) {
            println("No findings match. Run an analysis first: analyze <file>");
            return;
        }
        println("");
        println(String.format(Ansi.BOLD + "%-10s %-9s %-24s %-6s %-9s %s" + Ansi.RESET,
                "ID", "SEVERITY", "FILE", "LINE", "STATUS", "FINDING"));
        for (ReviewItem item : items) {
            println(String.format("%-10s %s%-9s%s %-24s %-6s %-9s %s",
                    item.getIssueId(),
                    sevColor(item.getSeverity()), item.getSeverity().getLabel(), Ansi.RESET,
                    truncate(item.getFileName(), 24),
                    item.getLineNumber() == 0 ? "file" : String.valueOf(item.getLineNumber()),
                    item.getStatus().getLabel(),
                    truncate(item.getTitle(), 60)));
        }
        Map<String, Integer> summary = controller.getReport().aggregateItems();
        println("");
        println(summary.get("total") + " finding(s): " + summary.get("pending") + " pending, "
                + summary.get("accepted") + " accepted, " + summary.get("rejected") + " rejected.");
        println("Use " + Ansi.BOLD + "show <ID>" + Ansi.RESET + " for the full detail.");
    }

    private void commandShow(String argument) {
        Optional<ReviewItem> item = controller.getReport().findById(argument);
        if (item.isEmpty()) {
            println("No finding with id '" + argument + "'. Use: issues");
            return;
        }
        ReviewItem finding = item.get();
        println("");
        println(sevColor(finding.getSeverity()) + Ansi.BOLD
                + finding.getSeverity().getLabel().toUpperCase() + Ansi.RESET
                + "  " + Ansi.BOLD + finding.getTitle() + Ansi.RESET);
        println(Ansi.DIM + finding.getIssueId() + "   "
                + controller.getRepoManager().relativise(finding.getFilePath())
                + ":" + finding.getLineNumber()
                + "   category: " + finding.getCategory()
                + "   found by: " + finding.getProducedBy() + Ansi.RESET);
        println("");
        println("WHY THIS MATTERS");
        println("  " + wrap(finding.getDescription(), 92, "  "));
        if (!finding.getSuggestion().isEmpty()) {
            println("");
            println("SUGGESTED FIX");
            println("  " + wrap(finding.getSuggestion(), 92, "  "));
        }
        println("");
        println("STATUS: " + finding.getStatus().getLabel()
                + (finding.getFeedback().isEmpty() ? "" : "  -  " + finding.getFeedback()));
        printSourceContext(finding);
    }

    /** Prints the lines around a finding, so the user can see it in place. */
    private void printSourceContext(ReviewItem finding) {
        if (finding.getLineNumber() <= 0) {
            return;
        }
        try {
            String code = controller.readFile(finding.getFilePath());
            String[] lines = code.split("\n", -1);
            int from = Math.max(0, finding.getLineNumber() - 4);
            int to = Math.min(lines.length, finding.getLineNumber() + 3);
            println("");
            println("SOURCE");
            for (int i = from; i < to; i++) {
                boolean isTarget = (i + 1) == finding.getLineNumber();
                println(String.format("  %s%4d %s %s" + Ansi.RESET,
                        isTarget ? Ansi.YELLOW : Ansi.DIM, i + 1,
                        isTarget ? ">" : "|", lines[i]));
            }
        } catch (IOException ignored) {
            // The source is a convenience; its absence must not break the command.
        }
    }

    // ------------------------------------------------------------------ F07, F12

    private void commandAccept(String argument) {
        String[] parts = argument.split("\\s+", 2);
        if (parts[0].isEmpty()) {
            println("Usage: accept <ID> [note]");
            return;
        }
        boolean done = controller.acceptFinding(parts[0], parts.length > 1 ? parts[1] : "");
        println(done
                ? Ansi.GREEN + "Accepted " + parts[0].toUpperCase()
                  + ". Undo with 'undo'." + Ansi.RESET
                : "No finding with id '" + parts[0] + "'.");
    }

    private void commandReject(String argument) {
        String[] parts = argument.split("\\s+", 2);
        if (parts[0].isEmpty()) {
            println("Usage: reject <ID> <reason>");
            println("The reason is stored in the agent's memory so this finding is not raised again.");
            return;
        }
        String reason = parts.length > 1 ? parts[1] : "";
        if (reason.isBlank()) {
            System.out.print("Reason for rejecting (this teaches the agent): ");
            System.out.flush();
            try {
                reason = input.readLine();
            } catch (IOException ignored) {
                reason = "";
            }
        }
        boolean done = controller.rejectFinding(parts[0], reason);
        println(done
                ? Ansi.GREEN + "Rejected " + parts[0].toUpperCase()
                  + " and recorded the reason in agent memory. Undo with 'undo'." + Ansi.RESET
                : "No finding with id '" + parts[0] + "'.");
    }

    private void commandSeverity(String argument) {
        String[] parts = argument.split("\\s+");
        if (parts.length < 2) {
            println("Usage: severity <ID> <CRITICAL|HIGH|MEDIUM|LOW|INFO>");
            return;
        }
        boolean done = controller.changeSeverity(parts[0], Severity.fromText(parts[1]));
        println(done ? "Severity updated. Undo with 'undo'." : "No finding with id '" + parts[0] + "'.");
    }

    private void commandBulkReject(String argument) {
        if (argument.isEmpty()) {
            println("Usage: dismissall <severity> [reason]   e.g. dismissall INFO not relevant here");
            return;
        }
        String[] parts = argument.split("\\s+", 2);
        Severity threshold = Severity.fromText(parts[0]);
        String reason = parts.length > 1 ? parts[1] : "Bulk dismissed by reviewer";
        int count = controller.bulkRejectAtOrBelow(threshold, reason);
        println(count == 0
                ? "No pending findings at or below " + threshold.getLabel() + "."
                : Ansi.GREEN + "Rejected " + count + " finding(s). This counts as one undo step."
                  + Ansi.RESET);
    }

    private void commandUndo() {
        Optional<String> undone = controller.undo();
        println(undone.map(d -> Ansi.GREEN + "Undone: " + d + Ansi.RESET)
                      .orElse("There is nothing to undo."));
    }

    private void commandRedo() {
        Optional<String> redone = controller.redo();
        println(redone.map(d -> Ansi.GREEN + "Redone: " + d + Ansi.RESET)
                      .orElse("There is nothing to redo."));
    }

    // ------------------------------------------------------------------ F10

    private void commandAsk(String argument) {
        if (argument.isEmpty()) {
            println("Usage: ask <question about this codebase>");
            return;
        }
        try {
            println("");
            println(controller.submitChat(argument, currentCode));
        } catch (LLMException e) {
            println(Ansi.RED + "The assistant could not answer: " + e.getUserMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F11

    private void commandAudit(String argument) {
        if (requireRepository()) {
            return;
        }
        String goal = argument.isEmpty()
                ? "Review this repository and report the most serious problems in it."
                : argument;
        try {
            println("");
            println(Ansi.BOLD + "AUTONOMOUS AUDIT" + Ansi.RESET + "  goal: " + goal);
            println(Ansi.DIM + "The agent plans, chooses tools, reads code and decides what to "
                    + "analyse. Each step is shown as it happens." + Ansi.RESET);
            println("");
            String conclusion = controller.runAudit(goal, 0, null);
            println("");
            println(Ansi.BOLD + "AGENT REPORT" + Ansi.RESET);
            println(conclusion);
            println("");
            println("Findings recorded during the audit are in " + Ansi.BOLD + "issues"
                    + Ansi.RESET + ".");
        } catch (LLMException e) {
            println(Ansi.RED + "The audit stopped: " + e.getUserMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F13

    private void commandSearch(String argument) {
        if (requireRepository()) {
            return;
        }
        if (argument.isEmpty()) {
            println("Usage: search <keywords>");
            return;
        }
        var snippets = controller.getAgent().getRetriever().retrieve(argument, 5, 10);
        if (snippets.isEmpty()) {
            println("Nothing matched '" + argument + "'.");
            return;
        }
        println("");
        for (var snippet : snippets) {
            println(Ansi.BOLD + controller.getRepoManager().relativise(snippet.getFilePath())
                    + Ansi.RESET + Ansi.DIM + "  (relevance "
                    + String.format("%.2f", snippet.getScore()) + ")" + Ansi.RESET);
            for (String line : snippet.getText().split("\n")) {
                println("    " + line);
            }
            println("");
        }
    }

    // ------------------------------------------------------------------ F08

    private void commandSnapshot(String argument) {
        if (argument.isEmpty()) {
            List<String> labels = controller.getSnapshotLabels();
            println(labels.isEmpty()
                    ? "No snapshots yet. Usage: snapshot <label> [commit hash]"
                    : "Snapshots: " + String.join(", ", labels));
            return;
        }
        String[] parts = argument.split("\\s+", 2);
        var snapshot = controller.takeSnapshot(parts[0], parts.length > 1 ? parts[1] : "");
        println("Saved snapshot " + Ansi.BOLD + parts[0] + Ansi.RESET
                + " with " + snapshot.size() + " finding(s).");
        println("Analyse the other version, snapshot it under a second label, then run: compare "
                + parts[0] + " <other label>");
    }

    private void commandCompare(String argument) {
        String[] parts = argument.split("\\s+");
        if (parts.length < 2) {
            println("Usage: compare <label A> <label B>");
            println("Available snapshots: " + String.join(", ", controller.getSnapshotLabels()));
            return;
        }
        try {
            DeltaReport delta = controller.compareSnapshots(parts[0], parts[1]);
            println("");
            println(delta.render());
        } catch (IOException e) {
            println(Ansi.RED + e.getMessage() + Ansi.RESET);
        }
    }

    private void commandCommits() {
        try {
            List<String> commits = controller.listCommits(15);
            println("");
            commits.forEach(c -> println("  " + c));
        } catch (IOException e) {
            println(Ansi.RED + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ F09

    private void commandExport(String argument) {
        if (argument.isEmpty()) {
            println("Usage: export <filename>        e.g. export review.pdf");
            println("Supported formats:");
            println(ExporterFactory.describe());
            return;
        }
        String[] parts = argument.split("\\s+");
        try {
            Path written = controller.exportReport(parts[0], parts.length > 1 ? parts[1] : null);
            println(Ansi.GREEN + "Report written to " + written + Ansi.RESET);
        } catch (IOException e) {
            println(Ansi.RED + "Export failed: " + e.getMessage() + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ memory, config

    private void commandMemory(String argument) {
        AgentMemory memory = controller.getAgent().getMemory();
        if ("clear".equalsIgnoreCase(argument)) {
            memory.forgetAll();
            println("Agent memory cleared.");
            return;
        }
        println("");
        println("Agent long-term memory: " + memory.size() + " remembered decision(s)");
        println(Ansi.DIM + "Stored at " + memory.getStorageLocation() + Ansi.RESET);
        if (memory.size() == 0) {
            println("Reject a finding with a reason and the agent will remember it here.");
            return;
        }
        for (AgentMemory.MemoryEntry entry : memory.getAll()) {
            println("  [" + entry.getDecision() + "] " + entry.getTitle()
                    + (entry.getReason().isEmpty() ? "" : "\n      reason: " + entry.getReason()));
        }
        println("");
        println("These are injected into future analysis prompts so the agent adapts to this team.");
    }

    private void commandConfig() {
        println("");
        println(controller.describeConfiguration());
        println("  provider          " + com.eecs3311.codereview.app.AppConfig
                .get(com.eecs3311.codereview.app.AppConfig.KEY_PROVIDER, "groq"));
        println("  model             " + com.eecs3311.codereview.app.AppConfig
                .get(com.eecs3311.codereview.app.AppConfig.KEY_MODEL, "(provider default)"));
        println("  api key           " + com.eecs3311.codereview.app.AppConfig.redactedApiKey());
        println("  indexed files     " + controller.getAgent().getRetriever().getIndexedFileCount());
        println("  coding rules      " + controller.getAgent().getStandardsChecker().getRulesSource());
        println("  agent tools       " + String.join(", ",
                controller.getAgent().getTools().names()));
        println("");
        println("Supported providers (set LLM_PROVIDER in .env):");
        com.eecs3311.codereview.llm.LLMProviderFactory.getPresets()
                .forEach((key, preset) -> println(String.format("  %-12s %s  (default model: %s)",
                        key, preset[0], preset[1])));
    }

    private void commandClear() {
        controller.getReport().clear();
        controller.getAgent().clearConversation();
        println("Findings and conversation cleared. Agent memory and history are unaffected.");
    }

    /** Shell commands people reach for out of habit while inside this prompt. */
    private static final java.util.Set<String> SHELL_COMMANDS = java.util.Set.of(
            "cd", "ls", "dir", "pwd", "cat", "clear", "mkdir", "rm", "cp", "mv", "echo",
            "git", "java", "javac", "mvn", "python", "python3", "code", "open", "nano",
            "vim", "sudo", "brew", "npm", "chmod", "curl", "wget", "man", "which", "touch");

    /**
     * Explains an unrecognised command.
     *
     * <p>This prompt looks like a shell but is not one, and the most common confusion is a
     * user typing a terminal command here - or, worse, trying to launch the application
     * again from inside itself. Saying only "unknown command" leaves them stuck, so a shell
     * command gets a specific explanation of which prompt they are at and how to leave it.</p>
     */
    private void printUnknownCommand(String command, String line) {
        boolean looksLikeShell = SHELL_COMMANDS.contains(command)
                || line.startsWith("./")
                || line.startsWith("/")
                || line.startsWith("~");

        if (!looksLikeShell) {
            println("Unknown command '" + command + "'. Type help for the command list.");
            return;
        }

        println("");
        println(Ansi.YELLOW + "  '" + command + "' is a terminal command, and you are "
                + "currently inside this application." + Ansi.RESET);
        println("");
        println("  There are two different prompts, and they accept different things:");
        println("");
        println("    " + Ansi.BOLD + "codereview(...)>" + Ansi.RESET
                + "   <- you are here; it takes the commands in 'help'");
        println("    " + Ansi.BOLD + "yourname@Mac %" + Ansi.RESET
                + "     <- your terminal; it takes cd, ls, ./run.sh and so on");
        println("");
        println("  To get back to your terminal, type " + Ansi.BOLD + "exit" + Ansi.RESET + ".");

        if (line.contains("run.sh") || line.contains("run.bat")) {
            println("");
            println("  (The application is already running - that is this prompt. You do not "
                    + "need to start it again.)");
        }
        if ("cd".equals(command)) {
            println("");
            println("  To review a different folder without leaving, use: "
                    + Ansi.BOLD + "open <path>" + Ansi.RESET);
        }
        if ("ls".equals(command) || "dir".equals(command)) {
            println("");
            println("  To list this repository's files, use: " + Ansi.BOLD + "files" + Ansi.RESET
                    + "  (or " + Ansi.BOLD + "tree" + Ansi.RESET + ")");
        }
        if ("cat".equals(command)) {
            println("");
            println("  'cat' does work here, but it takes a file inside the repository, "
                    + "for example: " + Ansi.BOLD + "cat LoginService.java" + Ansi.RESET);
        }
    }

    // ------------------------------------------------------------------ help

    private void printHelp() {
        println("");
        println(Ansi.BOLD + "REPOSITORY" + Ansi.RESET);
        println("  open <path>              Import a local folder                        [F01]");
        println("  open --clone <url>       Clone and import a remote Git repository     [F01]");
        println("  tree [depth]             Show the file tree                           [F01]");
        println("  files [filter]           List source files                            [F01]");
        println("  cat <file>               Show a file and make it the active selection [F01]");
        println("");
        println(Ansi.BOLD + "AI ANALYSIS" + Ansi.RESET);
        println("  summarize [file]         Explain what the code does                   [F02]");
        println("  analyze [file] [strat]   Detect issues with one strategy              [F03/F06]");
        println("  review [file]            Run every strategy over one file             [F03/F06]");
        println("  refactor [file|ISS-id]   Propose a rewrite, shown side by side        [F04]");
        println("  tests [file]             Generate unit tests                          [F05]");
        println("  ask <question>           Ask about the codebase (uses retrieval)      [F10]");
        println("  audit [goal]             Autonomous multi-step audit                  [F11]");
        println("  search <keywords>        Search the codebase semantically              [F13]");
        println("  strategies               List the analysis strategies");
        println("");
        println(Ansi.BOLD + "FINDINGS" + Ansi.RESET);
        println("  issues [filter]          List findings                                [F03]");
        println("  show <ID>                Full detail plus the source lines            [F03]");
        println("  accept <ID> [note]       Accept a finding                             [F07]");
        println("  reject <ID> <reason>     Reject it and teach the agent                [F07]");
        println("  severity <ID> <level>    Re-grade a finding                           [F07]");
        println("  dismissall <sev> [why]   Reject everything at or below a severity     [F07]");
        println("  undo / redo              Reverse or reapply the last action           [F12]");
        println("  history                  The full action log                          [F12]");
        println("  memory [clear]           What the agent has learned from you          [F07]");
        println("");
        println(Ansi.BOLD + "VERSIONS AND REPORTS" + Ansi.RESET);
        println("  commits                  Recent Git commits                           [F08]");
        println("  snapshot <label> [hash]  Save the current findings under a label      [F08]");
        println("  compare <labelA> <labelB>  Delta between two snapshots                [F08]");
        println("  export <file.pdf|md|html|json>  Write the report                      [F09]");
        println("");
        println(Ansi.BOLD + "SESSION" + Ansi.RESET);
        println("  usage                    Token and cost accounting for this session");
        println("  config                   Show the active model configuration");
        println("  reload                   Re-read .env without restarting");
        println("  clear                    Clear findings and conversation");
        println("  exit                     Quit");
    }

    // ------------------------------------------------------------------ helpers

    private boolean requireRepository() {
        if (!controller.isRepositoryLoaded()) {
            println("No repository is open. Use: open <folder path>");
            return true;
        }
        return false;
    }

    private String resolveTarget(String argument) {
        if (!argument.isEmpty()) {
            Optional<FileNode> node = controller.getRepoManager().findFile(argument.trim());
            if (node.isPresent()) {
                return node.get().getPath();
            }
            println("No file matching '" + argument + "'. Try: files " + argument);
            return null;
        }
        if (currentPath.isEmpty()) {
            println("No file selected. Give a file name, or select one with cat <file>.");
            return null;
        }
        return currentPath;
    }

    private void printFindings(List<ReviewItem> findings) {
        if (findings.isEmpty()) {
            println(Ansi.GREEN + "No issues found in this pass." + Ansi.RESET);
            return;
        }
        println("");
        for (ReviewItem finding : findings) {
            println(sevColor(finding.getSeverity()) + "  "
                    + String.format("%-9s", finding.getSeverity().getLabel()) + Ansi.RESET
                    + " " + Ansi.DIM + finding.getIssueId() + Ansi.RESET
                    + "  line " + (finding.getLineNumber() == 0 ? "-" : finding.getLineNumber())
                    + "  " + finding.getTitle());
        }
        println("");
        println("Use " + Ansi.BOLD + "show <ID>" + Ansi.RESET + " for detail, "
                + Ansi.BOLD + "accept/reject <ID>" + Ansi.RESET + " to triage.");
    }

    /** Severity colour, suppressed when the output is not an ANSI terminal. */
    private String sevColor(Severity severity) {
        return Ansi.RESET.isEmpty() ? "" : severity.getAnsiColor();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private String wrap(String text, int width, String indent) {
        if (text == null || text.isEmpty()) {
            return "(no description given)";
        }
        StringBuilder sb = new StringBuilder();
        int lineLength = 0;
        for (String word : text.split("\\s+")) {
            if (lineLength + word.length() > width) {
                sb.append('\n').append(indent);
                lineLength = 0;
            }
            sb.append(word).append(' ');
            lineLength += word.length() + 1;
        }
        return sb.toString().stripTrailing();
    }

    private String shortName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void println(String text) {
        System.out.println(text);
    }

    /** ANSI escape codes, disabled automatically when the output is not a terminal. */
    static final class Ansi {
        private static final boolean ENABLED = System.console() != null
                && !"false".equalsIgnoreCase(System.getenv("CLI_COLOR"));

        static final String RESET = ENABLED ? "\u001B[0m" : "";
        static final String BOLD = ENABLED ? "\u001B[1m" : "";
        static final String DIM = ENABLED ? "\u001B[2m" : "";
        static final String RED = ENABLED ? "\u001B[31m" : "";
        static final String GREEN = ENABLED ? "\u001B[32m" : "";
        static final String YELLOW = ENABLED ? "\u001B[33m" : "";
        static final String CYAN = ENABLED ? "\u001B[36m" : "";

        private Ansi() {
        }
    }
}
