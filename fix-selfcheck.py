"""Makes the verification harness deterministic: it checks the code, not a live API.

Run once from the project root.
"""
import pathlib

p = pathlib.Path("tools/SelfCheck.java")
s = p.read_text(encoding="utf-8")

# ---------------------------------------------------------------- force offline
old = '''    private static void checkEndToEnd(String repository) throws Exception {
        Path memory = Path.of(repository, ".agent-memory", "memory.json");
        Files.deleteIfExists(memory);

        AgentController controller = new AgentController();'''

new = '''    private static void checkEndToEnd(String repository) throws Exception {
        Path memory = Path.of(repository, ".agent-memory", "memory.json");
        Files.deleteIfExists(memory);

        // Deliberately run against the built-in offline analyser rather than whatever
        // provider happens to be configured. A verification harness has to be
        // reproducible: if it called a live API it would fail whenever that service was
        // rate limiting, offline or simply slow, which says nothing about this code. It
        // would also spend the user's quota every time they checked their build.
        System.setProperty("LLM_PROVIDER", "mock");
        com.eecs3311.codereview.llm.LLMClient.getInstance().reloadConfiguration();

        AgentController controller = new AgentController();'''

assert old in s, "checkEndToEnd anchor not found"
s = s.replace(old, new, 1)

# ---------------------------------------------------------------- never crash
old_guard = '''        check("analysis produces findings", !findings.isEmpty());
        check("findings reach the report", controller.getReport().getTotalIssues() > 0);
        check("at least one critical finding is detected",
                findings.stream().anyMatch(f -> f.getSeverity() == Severity.CRITICAL));

        ReviewItem first = findings.get(0);'''

new_guard = '''        check("analysis produces findings", !findings.isEmpty());
        check("findings reach the report", controller.getReport().getTotalIssues() > 0);
        check("at least one critical finding is detected",
                findings.stream().anyMatch(f -> f.getSeverity() == Severity.CRITICAL));

        if (findings.isEmpty()) {
            // Report the remaining checks as failed rather than throwing: a stack trace
            // tells the reader nothing about which behaviours are broken.
            check("triage, memory, undo, snapshot, export and audit could be checked",
                    false);
            System.out.println();
            System.out.println("   The analysis returned nothing, so the checks that depend");
            System.out.println("   on a finding were skipped. Run this to see the reason:");
            System.out.println("     ./run.sh --cli --repo " + repository
                    + " --command \\"analyze LoginService.java security\\"");
            return;
        }

        ReviewItem first = findings.get(0);'''

assert old_guard in s, "guard anchor not found"
s = s.replace(old_guard, new_guard, 1)

# ---------------------------------------------------------------- say so in the banner
old_banner = '''        System.out.println("AI Code Review Agent - self check (no external dependencies)");
        System.out.println("============================================================");'''
new_banner = '''        System.out.println("AI Code Review Agent - self check (no external dependencies)");
        System.out.println("============================================================");
        System.out.println("Runs against the built-in offline analyser, so the result is the");
        System.out.println("same every time and uses none of your API quota.");'''
assert old_banner in s
s = s.replace(old_banner, new_banner, 1)

p.write_text(s, encoding="utf-8")
print("SelfCheck now runs deterministically against the offline analyser")
