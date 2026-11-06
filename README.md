# AI Code Review Agent

**EECS3311 Course Project — Stage 2 implementation**

> **New to this project, or new to programming?** Start with
> **[GETTING-STARTED.md](GETTING-STARTED.md)** — a step-by-step setup guide that assumes no
> prior experience. For recording the demonstration videos, see
> **[docs/VIDEO-SCRIPT.md](docs/VIDEO-SCRIPT.md)**.

An AI agent that reviews source code. It imports a repository, plans its own multi-step
audit, chooses which files to read, analyses them with interchangeable review strategies,
explains what it found, proposes fixes and tests, and remembers which findings your team
rejected so it stops raising them.

It has both a **graphical interface** and a **command-line interface**, and every feature is
available in both.

---

## Table of contents

1. [What it does](#1-what-it-does)
2. [Prerequisites](#2-prerequisites)
3. [Local configuration and API key protection](#3-local-configuration-and-api-key-protection)
4. [Installing and building](#4-installing-and-building)
5. [Running the GUI](#5-running-the-gui)
6. [Running the CLI](#6-running-the-cli)
7. [Example usage walkthrough](#7-example-usage-walkthrough)
8. [Verifying the build](#8-verifying-the-build)
9. [Project structure](#9-project-structure)
10. [Design patterns implemented](#10-design-patterns-implemented)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. What it does

| ID | Feature | Type |
|----|---------|------|
| F01 | Repository import and file browsing (local folder or Git clone) | Deterministic |
| F02 | Code summarisation | AI |
| F03 | Issue detection with severity grading | AI |
| F04 | Refactoring suggestions with a side-by-side diff | AI |
| F05 | Unit-test generation | AI |
| F06 | Coding-standard checking against a `.rules` file | Hybrid |
| F07 | Accept / reject findings, capturing the reviewer's reasoning | Deterministic |
| F08 | Comparison of review results across two versions | Hybrid |
| F09 | Report export to PDF, Markdown, HTML and JSON | Deterministic |
| F10 | Natural-language assistant that answers questions about the codebase | AI |
| F11 | **Autonomous multi-step repository audit** (plans, picks tools, acts) | AI |
| F12 | **Undo / redo and a full action history** | Deterministic |
| F13 | **Codebase search and retrieval** | Deterministic |

**What makes it an agent rather than a chat wrapper.** Feature F11 runs a reasoning loop: the
agent turns your goal into a plan, then on each turn decides a single next action, invokes one
of five tools (`list_files`, `read_file`, `search_code`, `analyze_file`, `git_history`),
observes the result, and uses that observation to decide the next step. It recovers from tool
errors, refuses to repeat an action it has already taken, and writes a synthesis at the end.
Its short-term memory holds the run; its long-term memory holds your team's past decisions and
is injected into every future analysis.

---

## 2. Prerequisites

**Required**

- **Java Development Kit, version 17 or newer.** Check with `java -version`.
  - macOS: `brew install openjdk@21`
  - Ubuntu/Debian: `sudo apt install openjdk-21-jdk`
  - Windows: download from <https://adoptium.net>

**Optional**

- **Maven 3.8+** — only needed to run the JUnit test suite or build a jar.
  The application itself builds with `javac` alone (see [section 4](#4-installing-and-building)).
- **Git** — only needed for cloning remote repositories (F01) and comparing commits (F08).

**No third-party Java libraries are required.** HTTP calls, JSON parsing, PDF writing and
diffing are all implemented against the standard library, so the project builds from a clean
checkout with no downloads.

---

## 3. Local configuration and API key protection

The application needs an LLM API key for its AI features. **No key is stored in this
repository**, and `.env` is listed in `.gitignore`.

### Step 1 — get a free key

The default provider is **Groq**, which is free and does not require a credit card.

1. Go to <https://console.groq.com> and sign up.
2. Open **API Keys** and create a new key.
3. Copy it. It begins with `gsk_`.

Other supported providers: **OpenRouter** (free tier), **Ollama** (runs locally, no key at
all), and **OpenAI** (paid). See `.env.example` for each one's settings.

### Step 2 — run the setup script

```bash
./setup-key.sh          # Windows: setup-key.bat
```

It creates the configuration file, saves your key, confirms Git is ignoring it, and makes a
real request so you know the key works. That is the whole of steps 2 and 3; skip to step 4.

To do it by hand instead:

```bash
cp .env.example .env    # Windows: copy .env.example .env
```

### Step 3 — put your key in `.env`

Open `.env` in any text editor and replace the placeholder:

```
LLM_PROVIDER=groq
LLM_API_KEY=gsk_your_real_key_goes_here
```

Save the file. That is all the configuration needed.

### Step 4 — confirm the key is not tracked

```bash
git status
```

`.env` must **not** appear in the list. If it does, stop and check `.gitignore`.

> **Security rules this project follows**
> - The key is never written into source code, a report, a log line or a committed file.
> - It is read at run time from `.env`, an environment variable or a JVM system property.
> - It is sent only in the `Authorization` HTTP header.
> - Anywhere the key is displayed (the status bar, the CLI `config` command) it is redacted
>   to `****` plus the last four characters, so it is safe to show on screen during a demo.
> - If a key is ever committed by accident, **revoke it immediately** at the provider's
>   console. Deleting the file afterwards does not make the exposed key safe, because it
>   remains in the Git history.

### Running without a key

The application still starts. It enters **OFFLINE MODE**, shown as a banner in both
interfaces, and falls back to a small built-in static analyser. This is enough to explore the
interface, but it is deliberately weaker than the real system and does not demonstrate the
AI features.

---

## 4. Installing and building

### Option A — no Maven needed (recommended)

```bash
git clone <your-repository-url>
cd ai-code-review-agent
./build.sh
```

Windows:

```cmd
build.bat
```

### Option B — with Maven

```bash
mvn clean package
```

This also runs the JUnit test suite and produces `target/ai-code-review-agent.jar`, which can
be started with `java -jar target/ai-code-review-agent.jar`.

---

## 5. Running the GUI

```bash
./run.sh                                # empty window
./run.sh --repo samples/demo-project    # with a project already loaded
```

Windows: `run.bat`  ·  With Maven: `java -jar target/ai-code-review-agent.jar`

The window has four regions:

```
+-------------+--------------------------------+-----------------+
| Repository  |  Source viewer                 |  AI output      |
| file tree   |                                |  Assistant      |
|  [F01]      +--------------------------------+  Agent trace    |
|             |  Findings table                |                 |
|             |  [F03 / F06 / F07]             |                 |
+-------------+--------------------------------+-----------------+
| status bar - model in use, or OFFLINE MODE warning             |
+----------------------------------------------------------------+
```

| To do this | Use |
|---|---|
| Open a project | **Repository → Open folder…**, or **Open folder by path…** to paste one |
| Explain a file | Select it, then **Summarise** |
| Find defects | Select it, pick a strategy, then **Analyse** |
| Run every analysis | **Full review** |
| Propose a fix | **Refactor**, or select a finding and press **Fix this** |
| Write tests | **Generate tests** |
| Let the agent work on its own | **Run autonomous audit** |
| Ask a question | The **Assistant** tab |
| Triage a finding | Select a row, then **Accept** or **Reject** |
| Change your mind | **Undo** (also `Ctrl/Cmd+Z`) |
| Save the review | **Report → Export report…** |

Selecting text in the source viewer restricts the next analysis to that selection.

---

## 6. Running the CLI

```bash
./run.sh --cli                                  # interactive
./run.sh --cli --repo samples/demo-project      # interactive, project already open
./run.sh --cli --repo samples/demo-project --command "analyze LoginService.java security"
```

The last form runs one command, prints the result and exits, so the tool can be scripted in a
build pipeline.

Type `help` for the full command list. The main ones:

```
REPOSITORY            AI ANALYSIS                   FINDINGS
  open <path>           summarize [file]      F02     issues [filter]        F03
  open --clone <url>    analyze [file] [strat] F03    show <ID>              F03
  tree [depth]          review [file]         F03    accept <ID> [note]     F07
  files [filter]        refactor [file|ISS-id] F04    reject <ID> <reason>   F07
  cat <file>            tests [file]          F05    severity <ID> <level>  F07
                        ask <question>        F10    dismissall <sev>       F07
VERSIONS & REPORTS      audit [goal]          F11    undo / redo            F12
  commits         F08   search <keywords>     F13    history                F12
  snapshot <label>F08                                memory [clear]         F07
  compare <a> <b> F08   SESSION
  export <file>   F09     usage   config   reload   clear   exit
```

---

## 7. Example usage walkthrough

A sample project with deliberate defects is included at `samples/demo-project`, so there is
always something real to find.

```bash
./run.sh --cli --repo samples/demo-project
```

```
codereview(demo-project)> analyze LoginService.java security

  Critical  ISS-1001  line 15  Credential hard-coded in source
  Critical  ISS-1002  line 33  SQL query built by string concatenation
  Critical  ISS-1004  line 54  SQL query built by string concatenation
  Medium    ISS-1003  line 41  printStackTrace used instead of logging
  Medium    ISS-1005  line 67  Division without a zero guard

codereview(demo-project)> show ISS-1002
   ... full explanation, the suggested fix, and the surrounding source lines ...

codereview(demo-project)> reject ISS-1005 sessionCount is validated by the caller

  Rejected ISS-1005 and recorded the reason in agent memory. Undo with 'undo'.

codereview(demo-project)> memory
  [Rejected] Division without a zero guard
      reason: sessionCount is validated by the caller

codereview(demo-project)> audit find the most serious problems in this repository

  [step 1] PLAN         1. Call list_files to take an inventory ...
  [step 2] ACTION       list_files
  [step 2] OBSERVATION  Repository contains 3 source files ...
  [step 3] ACTION       search_code: password authentication
  [step 4] ACTION       analyze_file: LoginService.java|security
  ...
  [step 8] CONCLUSION   Audit complete

codereview(demo-project)> export review.pdf
  Report written to /.../review.pdf
```

The exported report contains the findings, your accept/reject decisions and their reasons, the
agent's reasoning trace, and the measured token usage for the session.

---

## 8. Verifying the build

**Full test suite (needs Maven and an internet connection the first time):**

```bash
mvn test
```

**No-dependency verification harness (needs nothing but the JDK):**

```bash
./build.sh --check
```

This runs 118 assertions covering every design pattern, the deterministic standards checker,
JSON tolerance to malformed model output, the diff engine, the exporters, and a full
end-to-end run: import a repository, analyse it, triage a finding, write it to agent memory,
undo, take a snapshot, export a PDF, and run an autonomous audit.

---

## 9. Project structure

```
ai-code-review-agent/
├── src/main/java/com/eecs3311/codereview/
│   ├── app/          Main, AgentController, AppConfig
│   ├── model/        ReviewItem (Memento), ReviewReport (Subject), FileNode (Composite)
│   ├── observer/     Observer, Subject
│   ├── agent/        ReviewAgent (Facade), AgentPlanner, AgentMemory, CodeRetriever
│   │   ├── strategy/ ReviewStrategy + AbstractReviewStrategy (Template Method) + 4 strategies
│   │   └── tools/    AgentTool + 5 tools + ToolRegistry
│   ├── llm/          LLMClient (Singleton), LLMProviderFactory (Factory), PromptBuilder
│   │   └── decorator/ Retrying, Caching, RateLimiting, Logging (Decorator)
│   ├── command/      ReviewCommand + 4 commands + CommandHistory (Command)
│   ├── export/       ReportExporter + 4 exporters + SimplePdfWriter (Template Method)
│   ├── repo/         RepoManager, StandardsChecker
│   ├── ui/           CodeReviewGUI, CLIView
│   ├── json/         Json (dependency-free parser and writer)
│   └── util/         Diff, SourceText
├── src/test/java/    JUnit 5 test suite
├── tools/SelfCheck.java      no-dependency verification harness
├── samples/demo-project/     sample code with deliberate defects, plus a .rules file
├── docs/diagrams/            UML class, use-case and sequence diagrams (+ generators)
├── stage2-report.pdf         the Stage 2 Implementation Report
├── docs/stage2-report-FILL-IN.html   fill-in-the-blanks page; print to PDF from a browser
├── .env.example              safe configuration template (no real keys)
├── setup-key.sh / .bat       creates .env, saves your key, verifies it works
└── build.sh / run.sh / build.bat / run.bat
```

---

## 10. Design patterns implemented

Ten distinct patterns, each solving a stated design problem. Section 3 of
`stage2-report.pdf` explains each one in full.

| Pattern | Key classes | Problem it solves |
|---|---|---|
| **Singleton** | `LLMClient` | One point of access to the model: one credential, one usage total, one shared cache, one throttle |
| **Strategy** | `ReviewStrategy` + 4 strategies | Interchangeable analyses without conditionals in the agent |
| **Command** | `ReviewCommand` + 4 commands, `CommandHistory` | Triage as objects, so actions can be logged and undone |
| **Observer** | `Subject`, `Observer`, `ReviewReport` | GUI and CLI stay current without polling or knowing each other |
| **Facade** | `ReviewAgent` | One entry point over nine collaborating AI components |
| **Composite** | `FileNode` | Uniform operations over a single file or a whole project tree |
| **Factory Method** | `LLMProviderFactory`, `ExporterFactory` | Choose a provider or an export format without naming a class |
| **Template Method** | `AbstractReviewStrategy`, `AbstractReportExporter` | Fix the invariant sequence; let subclasses vary the steps |
| **Decorator** | `RetryingProvider`, `CachingProvider`, `RateLimitingProvider`, `LoggingProvider` | Compose retrying, caching, throttling and metering independently |
| **Memento** | `ReviewItem.Memento` | Capture triage state so commands can undo without touching internals |

A **Builder** (`PromptBuilder`) is also used to assemble prompts from optional parts.

---

## 11. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "OFFLINE MODE" banner | No API key found. Check that `.env` exists in the project folder, that it contains `LLM_API_KEY=...`, and that the value is not still the placeholder. Then restart, or type `reload` in the CLI. |
| `The API key was rejected` | The key is wrong or revoked. Generate a new one at your provider's console. |
| `The model name was not found` | The model was renamed or retired. Set `LLM_MODEL` in `.env` to a current model from your provider's model list. |
| `Rate limit reached` | Free tiers cap requests per minute. Wait a moment; the application throttles itself, but several rapid audits can still hit the limit. |
| `javac: command not found` | The JDK is not installed or not on your PATH. See [prerequisites](#2-prerequisites). |
| GUI does not open | On a machine with no display the application falls back to the CLI and says so. Use `--cli`. |
| `git clone failed` | Git is not installed, or the URL is wrong or private. Import a local folder instead. |
| Version comparison says "not a Git repository" | F08 needs a folder containing `.git`. Import the repository root rather than a subfolder. |
| Maven cannot download JUnit | Use `./build.sh --check` instead; it needs no downloads. |

---

## Licence and academic note

Coursework for EECS3311 at York University. The sample code in `samples/demo-project`
contains deliberate security defects for demonstration purposes and must not be reused.
