# Demonstration video script

Every implemented feature needs a video segment. This script gives you the exact clicks, the
exact commands, and what to say.

**Plan:** record **three videos** rather than sixteen. It is far less work, and the course
allows it as long as every feature has a clearly labelled segment with a direct timestamped
link.

| Video | Covers | Interface | Length |
|---|---|---|---|
| **Video 1** | F01, F02, F03, F04, F05, F07, F09, F10, F12 | GUI | ~12 min |
| **Video 2** | F01, F06, F08, F13, F09 (CLI export) | CLI | ~8 min |
| **Video 3** | F11, plus the three error/offline cases | GUI + CLI | ~7 min |

---

## Before you press record

1. **Hide your API key.** Close any editor showing `.env`. Close your Groq console tab.
   The program already redacts the key to `****` plus four characters, but your editor does not.
2. **Check you are online and the key works.** Run `./run.sh --cli --repo samples/demo-project`,
   type `config`, and confirm you see your model name and `****` — **not** "OFFLINE MODE".
   Then type `exit`.
3. **Reset the demo data** so your run is clean:
   ```
   rm -rf samples/demo-project/.agent-memory
   ```
   (Windows: `rmdir /s /q samples\demo-project\.agent-memory`)
4. **Make your text big enough to read.** In the CLI, increase your terminal font size.
   Maximise the GUI window.
5. **Turn your microphone on.** A silent video of clicking does not explain anything.
6. **Write down the time** when each feature starts. You need timestamps for the report.

### How to open each segment

Say this every time, so the marker can find and grade the feature:

> "Feature F-zero-three, Issue Detection and Severity Categorization. I'm going to select a
> file and run the security analysis. The AI is involved here — the model receives the code and
> returns structured findings, which the program parses and grades."

---

# Video 1 — The graphical interface

Start with the window closed. Run `./run.sh` (Windows: `run.bat`).

---

### 00:00 — Introduction

> "This is the AI Code Review Agent, my EECS3311 Stage 2 project. It's an AI agent that
> reviews source code. It has a graphical interface, which I'll show in this video, and a
> command-line interface, which I'll show in video two."

Point at the status bar at the bottom.

> "Down here it shows which model is connected — I'm using Llama 3.3 70B through Groq — and my
> API key, redacted to the last four characters so it's never exposed."

---

### F01 — Repository Import and File Browsing

> "Feature F01, Repository Import and File Browsing. This one is deterministic — no AI."

1. **Repository → Open folder…**
2. Navigate to `samples/demo-project` and open it.
3. Expand the tree down to the three `.java` files.

> "The program has scanned the folder, skipped build output and dependency folders, and built a
> tree. The tree is implemented with the Composite pattern, which is why the same operations
> work on one file or the whole project. It also indexed the files for search and loaded the
> project's coding-standard rules."

4. Click **LoginService.java**.

> "The code appears here. This sample file has deliberate bugs in it, so there's always
> something real to find."

---

### F02 — Code Summarisation

> "Feature F02, Code Summarisation. This is AI-based — the model reads the code and explains
> it."

1. With `LoginService.java` selected, press **Summarise**.
2. Wait. Read the result out loud, briefly.

> "It gives four sections: purpose, how it works, dependencies and risks. The prompt asks for
> those specific sections — an open-ended 'summarise this' just gets you a paraphrase of the
> code instead of an explanation of it."

---

### F03 — Issue Detection and Severity Categorisation

> "Feature F03, Issue Detection with severity grading. AI-based."

1. Check the **Strategy** dropdown shows `security`.
2. Press **Analyse**.
3. When findings appear, point at the table.

> "Five findings, graded by severity. Red is critical. The model returns these as structured
> JSON, and the program validates them — if the model gives a line number that doesn't exist in
> the file, it's corrected to file-wide rather than thrown away."

4. Click the first finding.

> "The explanation appears on the right, and the code viewer jumps to that exact line. This one
> is a hard-coded database password."

5. Open the **Strategy** dropdown to show all four.

> "There are four strategies — security, style, performance and maintainability. They're
> interchangeable because of the Strategy pattern. Adding the performance one took a single new
> class and no change to the agent at all."

---

### F04 — Refactoring Suggestions

> "Feature F04, Refactoring Suggestions. AI-based."

1. Click the **SQL query built by string concatenation** finding.
2. Press **Fix this**.
3. Wait for the dialog.

> "Original on the left, the model's rewrite on the right, with the line counts changed. The
> diff uses a longest-common-subsequence algorithm — a naive line-by-line comparison marks
> everything after an inserted line as changed, which makes this unreadable."

4. Scroll the explanation panel at the top.

> "And it explains each change. Note that nothing has been written to my source files — this is
> a suggestion, and there's a Copy button if I want it."

5. Close the dialog.

---

### F05 — Test Generation

> "Feature F05, Test Generation. AI-based."

1. In the tree, click **PriceCalculator.java**.
2. Press **Generate tests**.

> "It picked JUnit 5 because this is a Java file — it infers the framework from the language,
> and falls back to the standard library framework if it can't tell. The prompt requires one
> behaviour per test, real assertions rather than placeholders, and coverage of the typical
> case, the boundaries and the failure paths."

3. Scroll through the generated tests and point at one boundary test.

---

### F07 — Accept / Reject and Feedback Capture

> "Feature F07, Review Acceptance and Rejection with human feedback capture. Deterministic."

1. Go back to `LoginService.java` and press **Analyse** if the table is empty.
2. Select the **Division without a zero guard** finding.
3. Press **Reject**.
4. Type: `sessionCount is always validated by the caller on this project`
5. Press OK.

> "The row greys out, and — this is the important part — the reason I gave is now in the
> agent's long-term memory."

6. **Findings → Agent memory…**

> "There it is, stored beside the repository. The next time the agent analyses related code,
> this gets injected into the prompt as an instruction not to raise that finding again. That's
> what makes the agent adapt to a team rather than repeating itself."

7. Close the dialog.

---

### F12 — Undo, Redo and Action History

> "Feature F12, Undo and action history. This is new in Stage 2 — it wasn't in my Stage 1
> design."

1. Press **Undo** in the toolbar.

> "The finding is back to Pending. This works because of the Memento pattern: before each
> command changes a finding, it takes a snapshot of the state, and undo hands that snapshot
> back."

2. Press **Redo**, then **Undo** again.
3. **Findings → Dismiss all Info findings**, then press **Undo**.

> "A bulk action undoes as a single step, which is what you'd expect after one click."

4. **Findings → Action history…**

> "And the log keeps every action with a timestamp, including the undos. That log goes into the
> exported report."

---

### F10 — Natural Language AI Interface

> "Feature F10, the natural-language assistant. AI-based, and it uses retrieval."

1. Click the **Assistant** tab on the right.
2. Type: `Where are database credentials handled, and what is wrong with how it is done?`
3. Press Send. Wait.

> "Notice I didn't have that file open. The question isn't sent straight to the model — the
> program first searches the whole repository for the parts relevant to the question and
> attaches only those. That's what lets it answer about a codebase much bigger than the model's
> context window."

4. Point at the **Sources consulted** list at the end of the answer.

> "And it tells me which files and line numbers it used, so I can check the answer."

5. Ask a follow-up: `How would I fix that?`

> "It remembers the conversation, so follow-ups work."

---

### F09 — Report Generation

> "Feature F09, Report Generation. Deterministic."

1. **Report → Export report…**
2. Choose **pdf**, save it as `demo-review.pdf` somewhere you can find.
3. Open the PDF.

> "The report has the summary counts, the findings grouped by file with their severities, my
> accept and reject decisions with the reasons I gave, the agent's reasoning trace, and the
> measured token usage for the session."

4. Scroll through it.

> "This PDF is generated without any PDF library — the project has zero runtime dependencies,
> so I wrote the PDF writer against the file format directly. Markdown, HTML and JSON are also
> supported, through a factory."

---

### End of Video 1

> "That's the graphical interface. Video two covers the command line, including the coding
> standard check and version comparison."

---

# Video 2 — The command-line interface

Run: `./run.sh --cli --repo samples/demo-project`

---

### 00:00 — Introduction

> "This is the command-line interface. Every feature in the GUI is available here too, because
> both interfaces call the same controller — they can't drift apart in behaviour."

Type `help` and scroll through.

> "Every command is tagged with the feature it implements."

---

### F01 — Repository browsing (CLI)

```
tree 5
files .java
cat PriceCalculator.java
```

> "Feature F01 again, from the command line. Same Composite tree, rendered as text."

---

### F06 — Coding-Standard Checking

> "Feature F06, Coding-Standard Checking. This one is **hybrid** — part deterministic, part AI
> — and I want to show exactly where the split is."

1. Show the rules file first. Open `samples/demo-project/.rules` in an editor, or:

```
exit
```
then `cat samples/demo-project/.rules` and restart the CLI.

> "This is the project's rule file. Max line length 100, no tabs, no wildcard imports, maximum
> five parameters, and so on."

2. Back in the CLI:

```
analyze LoginService.java style
```

3. Point at the findings.

> "Some of these came from the rule engine — the wildcard import, the unbraced conditional.
> Those are decided by parsing, with no model call, because they're exactly decidable. The rest
> came from the model, which is only asked about the judgement calls: whether a name actually
> describes what it holds, whether a comment is worth its line."

```
show ISS-1001
```

> "And the prompt tells the model the active rules **and** tells it not to report them, so the
> two halves don't both report the same violation. That was a real bug I had to fix — it's
> defect D3 in my report."

---

### F13 — Codebase Search and Retrieval

> "Feature F13, Codebase Search. New in Stage 2. Deterministic."

```
search password validation
```

> "This scores every file against the query using TF-IDF weighting, so a word that appears in
> every file counts for almost nothing and a rare word counts for a lot. It also splits camel
> case, so searching 'password' matches `getUserPassword`. This is the same mechanism the
> assistant uses to pick context, and the same one the autonomous agent uses as a tool."

---

### F08 — Comparison of Reviews Across Versions

> "Feature F08, Comparison Across Versions. Hybrid — the comparison itself is deterministic,
> the verdict on it is AI."

```
analyze LoginService.java security
snapshot before
```

> "That's the first version's findings saved under a label."

```
clear
analyze PriceCalculator.java security
analyze LoginService.java style
snapshot after
compare before after
```

> "Resolved, newly introduced, still present — and an AI assessment of what the change means.
> The matching deliberately ignores line numbers, so if someone inserts a line above a bug, it
> doesn't show up as 'old bug resolved, new bug introduced'."

If the folder is a Git repository, also show:

```
commits
```

---

### F09 — Export from the CLI

```
export cli-review.html
export cli-review.json
```

> "Same report, four formats, chosen by a factory. The JSON one is machine-readable so this
> could run in a CI pipeline."

```
usage
```

> "And the program meters its own token usage, broken down by feature. That's where the numbers
> in section 5 of my report come from — they're measured, not estimated."

---

### End of Video 2

```
exit
```

---

# Video 3 — The autonomous agent and error handling

---

### F11 — Autonomous Multi-Step Repository Audit

> "Feature F11, the Autonomous Multi-Step Repository Audit. This is new in Stage 2, and it's
> the feature that makes this an agent rather than something that forwards prompts to an API."

Run `./run.sh`, open `samples/demo-project`.

1. Press **Run autonomous audit**.
2. Change the goal to: `Find the most serious security problems in this repository and tell me which to fix first`
3. Press OK.
4. Switch to the **Agent trace** tab and let it run. Narrate as it goes:

> "Step one, it plans. It's been shown the real file inventory, so the plan names files that
> actually exist."

> "Now it decides a single next action per turn. It chose `list_files` first — it can't pick
> targets before it knows what's there."

> "Now `search_code`, to find where credentials are handled. Notice it's choosing this based on
> what it observed in the previous step, not following a fixed script."

> "Now `analyze_file`. This is the only tool with a side effect — findings from it go into the
> normal findings table so I can triage them afterwards."

> "And a closing synthesis over everything it observed."

5. Switch to the **Findings** table.

> "And here are the findings it recorded, ready to accept or reject like any other."

6. Explain the safety design:

> "Three things keep this loop from going wrong. It can only pick from five named tools — it
> never executes model output as code. A repeated action is blocked and fed back to it as an
> observation. And there's a hard step budget, so a run always terminates."

---

### Error handling 1 — Invalid repository path

Open the CLI: `./run.sh --cli`

```
open /this/path/does/not/exist
```

> "The error says exactly what's wrong and what to do about it. There are separate messages for
> a path that doesn't exist, a path that's a file rather than a folder, and a folder I don't
> have permission to read — that's the alternative flow in use case UC01."

---

### Error handling 2 — API failure and retry

The cleanest way to show this: disconnect your network, then run an analysis.

1. Turn off your Wi-Fi.
2. In the GUI (with a repository already open and a file selected), press **Analyse**.
3. Watch the status bar.

> "It's retrying automatically — that's the Retrying decorator, with exponential backoff.
> That's the alternative flow drawn in my sequence diagram SD01."

4. When the dialog appears:

> "Only after the automatic retries are exhausted does it ask me. And the message says what
> actually went wrong, not a stack trace."

5. Turn Wi-Fi back on and press **Retry** to show it recovering.

---

### Offline mode and local key configuration

1. Close the application.
2. Rename your `.env` temporarily:
   ```
   mv .env .env.backup
   ```
   (Windows: `ren .env .env.backup`)
3. Run `./run.sh`.

> "With no key configured, it doesn't crash — it warns me clearly and falls back to a small
> built-in static analyser so the interface still works. But it's labelled OFFLINE MODE in the
> status bar and in a dialog on startup, so this can never be mistaken for the real system.
> Every AI feature in these videos was recorded with a real model."

4. Close it, restore the key:
   ```
   mv .env.backup .env
   ```
5. Show the configuration safety:

> "And about the key itself —"

Run `./run.sh --cli` and type `config`.

> "It's redacted to four characters. It's never written into source code, never logged, never
> put in a report, and `.env` is in `.gitignore` so it can't be committed by accident."

Then show:

```
exit
git status
```

> "`.env` doesn't appear. Only `.env.example`, which has a placeholder in it."

---

### Closing

> "That's all thirteen features. The source code, the Stage 2 report with the updated UML
> diagrams, and the README are all in the repository. Thank you."

---

# After recording

1. Upload each video. **YouTube: set to Unlisted. Google Drive: Share → Anyone with the link →
   Viewer.**
2. **Test every link in a private browsing window.** If it asks for access, your marker will see
   the same thing and the segment counts as missing.
3. Note the timestamp where each feature starts.
4. On YouTube you can link a timestamp directly: `https://youtu.be/VIDEOID?t=372` (372 seconds).
   On Drive, write the timestamp next to the link in the report.
5. Fill in section 7 of `docs/stage2-report.md`, then rebuild the PDF with
   `cd docs && ./build-report.sh`.
6. Commit and push.
