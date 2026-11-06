package com.eecs3311.codereview.app;

import com.eecs3311.codereview.ui.CLIView;
import com.eecs3311.codereview.ui.CodeReviewGUI;

import java.awt.GraphicsEnvironment;

/**
 * The application entry point.
 *
 * <p>Chooses between the two user interfaces. Both are built on the same
 * {@link AgentController}, so neither has capabilities the other lacks.</p>
 *
 * <pre>
 *   java -jar ai-code-review-agent.jar                    launch the GUI
 *   java -jar ai-code-review-agent.jar --cli              launch the CLI
 *   java -jar ai-code-review-agent.jar --cli --repo DIR   CLI with a repository open
 *   java -jar ai-code-review-agent.jar --cli --repo DIR --command "analyze App.java security"
 *   java -jar ai-code-review-agent.jar --help             usage
 *   java -jar ai-code-review-agent.jar --version          version
 * </pre>
 */
public final class Main {

    private static final String VERSION = "2.0.0";

    private Main() {
    }

    public static void main(String[] args) {
        String mode = "gui";
        String repository = null;
        String command = null;

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            switch (argument) {
                case "--cli":
                case "-c":
                    mode = "cli";
                    break;
                case "--gui":
                case "-g":
                    mode = "gui";
                    break;
                case "--repo":
                case "-r":
                    if (i + 1 < args.length) {
                        repository = args[++i];
                    }
                    break;
                case "--command":
                case "-x":
                    if (i + 1 < args.length) {
                        command = args[++i];
                        mode = "cli";
                    }
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                case "--version":
                case "-v":
                    System.out.println("AI Code Review Agent " + VERSION
                            + " (EECS3311 Course Project, Stage 2)");
                    return;
                default:
                    if (argument.startsWith("-")) {
                        System.err.println("Unknown option: " + argument);
                        printUsage();
                        System.exit(2);
                    }
            }
        }

        // A headless machine cannot show a window; fall back rather than crashing.
        if ("gui".equals(mode) && GraphicsEnvironment.isHeadless()) {
            System.err.println("No display is available, so the GUI cannot start. "
                    + "Falling back to the command-line interface.");
            mode = "cli";
        }

        AgentController controller = new AgentController();

        if ("cli".equals(mode)) {
            CLIView cli = new CLIView(controller);
            if (command != null) {
                System.exit(cli.runOnce(repository, command));
            }
            if (repository != null) {
                try {
                    controller.handleImport(repository, false);
                } catch (Exception e) {
                    System.err.println("Could not open " + repository + ": " + e.getMessage());
                }
            }
            cli.start();
        } else {
            // --repo also applies to the window, which saves a first-time user from the
            // folder picker entirely. The GUI reads the already-loaded state when it is
            // constructed, so the tree and findings table are populated on open.
            if (repository != null) {
                try {
                    controller.handleImport(repository, false);
                } catch (Exception e) {
                    System.err.println("Could not open " + repository + ": " + e.getMessage());
                }
            }
            CodeReviewGUI.launch(controller);
        }
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("AI Code Review Agent " + VERSION
                + "  -  EECS3311 Course Project, Stage 2");
        System.out.println();
        System.out.println("USAGE");
        System.out.println("  java -jar ai-code-review-agent.jar [options]");
        System.out.println();
        System.out.println("OPTIONS");
        System.out.println("  -g, --gui                Launch the graphical interface (default)");
        System.out.println("  -c, --cli                Launch the command-line interface");
        System.out.println("  -r, --repo <folder>      Open this repository at startup");
        System.out.println("  -x, --command \"<cmd>\"    Run one CLI command, print the result, exit");
        System.out.println("  -h, --help               Show this message");
        System.out.println("  -v, --version            Show the version");
        System.out.println();
        System.out.println("EXAMPLES");
        System.out.println("  java -jar ai-code-review-agent.jar");
        System.out.println("  java -jar ai-code-review-agent.jar --cli --repo samples/demo-project");
        System.out.println("  java -jar ai-code-review-agent.jar --cli --repo samples/demo-project \\");
        System.out.println("       --command \"analyze LoginService.java security\"");
        System.out.println();
        System.out.println("CONFIGURATION");
        System.out.println("  Copy .env.example to .env and add your API key.");
        System.out.println("  Never commit .env; it is already listed in .gitignore.");
        System.out.println();
    }
}
