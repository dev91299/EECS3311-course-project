# Getting started — a step-by-step guide

**Read this first.** It assumes you have never written code. Every step says exactly what to
type and what you should see. Follow them in order.

Total time: about **1 hour** for setup, plus however long you spend recording videos.

---

## What you have

A complete, working Stage 2 project:

| File or folder | What it is |
|---|---|
| `src/` | The program itself — about 50 Java files |
| `samples/demo-project/` | A small fake project with deliberate bugs, so your demo always finds something |
| `tools/SelfCheck.java` | A self-test that proves the program works |
| `stage2-report.pdf` / `.docx` | The Stage 2 Implementation Report |
| `docs/diagrams/` | The UML diagrams (class, use-case, 4 sequence diagrams) |
| `README.md` | The technical README your marker will read |
| `.env.example` | A safe configuration template with **no real key** |
| `build.sh` / `run.sh` | Mac and Linux: build and run |
| `build.bat` / `run.bat` | Windows: build and run |

### What you still have to do yourself

1. Put the files on your computer and run them once (Steps 1–5).
2. Push to GitHub (Step 6).
3. Make some real commits over the next few days (Step 7).
4. Record the demonstration videos (Step 8).
5. Fill in the blanks in the report (Step 9).
6. Submit (Step 10).

---

## Step 1 — Install Java

The program is written in Java, so your computer needs Java installed.

### Check whether you already have it

Open a terminal:

- **Windows:** press the Windows key, type `cmd`, press Enter.
- **Mac:** press `Cmd + Space`, type `terminal`, press Enter.
- **Linux:** press `Ctrl + Alt + T`.

Type this and press Enter:

```
java -version
```

**If you see something with a number 17 or higher** (for example `openjdk version "21.0.2"`),
you already have Java. Skip to Step 2.

**If you see "command not found"** or a number below 17, install it:

- **Windows:** go to <https://adoptium.net>, click the big download button, run the installer,
  and click Next until it finishes. Then **close and reopen** your terminal.
- **Mac:** go to <https://adoptium.net>, download the macOS installer, run it.
- **Linux (Ubuntu):** type `sudo apt install openjdk-21-jdk` and press Enter.

Check again with `java -version`. You must see 17 or higher before continuing.

> **Why:** Java is the language the program is written in. Without it, nothing will run.

---

## Step 2 — Put the project on your computer

Download the project folder (`ai-code-review-agent`) and unzip it somewhere you can find
again. Your Desktop is fine.

Now tell your terminal to go into that folder. Type `cd ` (with a space after it), then **drag
the folder from your file manager onto the terminal window** and press Enter. The path fills
itself in, so you cannot mistype it.

You should now see something ending in `ai-code-review-agent`.

Check you are in the right place:

- **Mac/Linux:** type `ls` and press Enter.
- **Windows:** type `dir` and press Enter.

You should see `README.md`, `src`, `samples`, `pom.xml` and the others.

> **Why:** the terminal needs to be "inside" the project folder, the same way a file manager
> needs you to open a folder before you can use what is in it.

---

## Step 3 — Build the program

**Mac or Linux:**

```
./build.sh --check
```

**Windows:**

```
build.bat --check
```

This takes about 30 seconds. You should see a long list ending with:

```
  110 passed, 0 failed
```

**That line is important.** It means every part of the program works. If you ever change
something and want to check you did not break it, run this again.

> **If you get "permission denied" on Mac or Linux:** type `chmod +x build.sh run.sh` first,
> then try again.

---

## Step 4 — Get a free API key

The program needs to talk to an AI model. The one it uses by default is **free** and does not
ask for a credit card.

1. Go to <https://console.groq.com>
2. Sign up (Google or email is fine).
3. On the left, click **API Keys**.
4. Click **Create API Key**, give it any name, click Submit.
5. **Copy the key immediately.** It starts with `gsk_`. You cannot see it again after closing
   the box — if you lose it, just make another one.

> **Why Groq:** it is free (about 14,400 requests a day), fast, and needs no payment details.
> The program also supports OpenRouter, OpenAI and a locally-run model; see `.env.example`.

---

## Step 5 — Put your key in the configuration file

### 5a. Make your own copy of the template

**Mac or Linux:**

```
cp .env.example .env
```

**Windows:**

```
copy .env.example .env
```

### 5b. Put your key in it

Open the new `.env` file in any text editor (Notepad, TextEdit, VS Code — anything).

> **Can't see the file?** Files starting with a dot are hidden.
> On Mac press `Cmd + Shift + .` in Finder. On Windows, in File Explorer go to
> **View → Show → Hidden items**.

Find this line:

```
LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE
```

Replace the placeholder with your real key, so it looks like:

```
LLM_API_KEY=gsk_abc123yourrealkeyhere
```

Save and close.

### 5c. Check the key is safe

Type:

```
git status
```

`.env` must **not** appear anywhere in the output. If it does, stop and tell me — something is
wrong with `.gitignore`.

> **Why this matters:** an API key is like a password. If you push it to a public GitHub
> repository, anyone can use it, and the course explicitly penalises this. The project is set
> up so `.env` can never be committed by accident.
>
> **If you ever do leak a key:** go straight to the Groq console and delete it, then make a new
> one. Deleting the file afterwards is not enough, because the key stays in the Git history.

---

## Step 6 — Run it

### Try the graphical version

**Mac/Linux:** `./run.sh`   **Windows:** `run.bat`

A window opens. Try this:

1. **Repository → Open folder…** and choose the `samples/demo-project` folder inside the
   project.
2. In the tree on the left, click the arrows to expand down to `LoginService.java` and click it.
   The code appears in the middle.
3. Press **Analyse** in the toolbar.
4. Wait a few seconds. Findings appear in the table, coloured red for critical.
5. Click a finding. The explanation appears on the right and the code jumps to that line.
6. Press **Run autonomous audit**, accept the suggested goal, and press OK. Watch the **Agent
   trace** tab on the right fill up with the agent's reasoning.

Close the window when you are done.

### Try the command-line version

**Mac/Linux:** `./run.sh --cli --repo samples/demo-project`
**Windows:** `run.bat --cli --repo samples/demo-project`

At the `codereview(demo-project)>` prompt, try typing these one at a time:

```
analyze LoginService.java security
show ISS-1001
reject ISS-1005 this is checked by the caller
memory
undo
audit find the security problems here
export myreport.pdf
exit
```

> **If you see "OFFLINE MODE" in yellow:** your key is not being read. Check that `.env` is in
> the project folder (not inside `src` or anywhere else), that the line says
> `LLM_API_KEY=gsk_...` with no spaces around the `=`, and that you saved the file. Then type
> `reload` in the CLI, or restart.

---

## Step 7 — Put it on GitHub

You already have a public repository from Stage 1. **Use the same one.**

### 7a. Install Git if you do not have it

Type `git --version`. If it is missing, get it from <https://git-scm.com/downloads>.

### 7b. Connect this folder to your repository

Replace `YOUR-USERNAME` and `YOUR-REPO` with your real ones:

```
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPO.git
git branch -M main
```

### 7c. Set your name on the commits

```
git config user.name "Your Real Name"
git config user.email "your-github-email@example.com"
```

Use the email attached to your GitHub account, otherwise the commits will not show as yours.

### 7d. Push

```
git push -u origin main --force
```

> `--force` is needed because this replaces whatever is currently on the branch. If your Stage 1
> report is in that repository and you want to keep it, copy it into this folder **before**
> pushing, and commit it.

GitHub will ask you to log in. If it asks for a password, it wants a **personal access token**,
not your account password: GitHub → Settings → Developer settings → Personal access tokens →
Tokens (classic) → Generate new token → tick `repo` → copy and paste it as the password.

### 7e. Check it worked

Open your repository page in a browser. You should see the files, and clicking **commits**
should show 31 of them.

**Also check:** `.env` must **not** be there. Only `.env.example`.

### 7f. Make the repository public

Your repository page → **Settings** → scroll to the bottom → **Change visibility** → Public.

---

## Step 8 — Make real commits before 9 November

**Please read this part carefully — it is the one thing I could not do for you.**

The course requires at least 5 meaningful commits in each of three periods:

| Period | Dates | Status |
|---|---|---|
| 1 | 20–26 October | **Already passed.** |
| 2 | 27 October – 2 November | **Already passed.** |
| 3 | 3–9 November | **You can still satisfy this.** |

The 31 commits in this project are all dated today. That genuinely covers period 3.

I did not fake dates on the commits for periods 1 and 2. Backdating them would misrepresent
when the work happened, which is a different thing from the AI assistance the course asks you
to use and document. **The penalty for missing a period is a single 5-point deduction from the
Stage 2 grade in total** — not 5 points per missing commit and not per period. It is one
deduction, and everything else in this submission is built to score full marks.

### What to do now

Push today, then make a few genuine commits on the remaining days. Real small improvements
count; empty or formatting-only commits do not. Good options, each worth one commit:

- Add a test to `src/test/java/.../` for something not covered yet.
- Add a file with a different kind of bug to `samples/demo-project/` and check the agent finds it.
- Adjust a rule in `samples/demo-project/.rules` and verify the standards checker respects it.
- Change a prompt in one of the strategies and note whether the findings improve.
- Fix anything you notice while recording your videos.

After each change:

```
git add -A
git commit -m "Describe what you actually changed"
git push
```

Then list your commits for the report:

```
git log --since=2026-11-03 --until=2026-11-10 --pretty=format:"%h  %ad  %s" --date=short
```

---

## Step 9 — Record the demonstration videos

Every feature needs a video segment. See **`docs/VIDEO-SCRIPT.md`** — it gives you the exact
clicks, the exact commands to type, and what to say, feature by feature.

The short version:

- **Recording:** Windows — press `Win + G` for Game Bar, or use OBS (free). Mac — press
  `Cmd + Shift + 5`. Either way record the screen **with your microphone on**.
- **Before you start:** open the CLI and type `config`. Confirm your key shows as `****` plus
  four characters. **The program already hides it, but double-check nothing else on screen
  shows it** — close your text editor if `.env` is open in it.
- **For each feature, say out loud:** the feature ID and name, what you are about to do, and —
  for the AI features — where the AI is involved.
- **Also record:** the two error cases and the offline warning (the last three rows of the
  report's section 7 table).
- **Uploading:** YouTube (set to **Unlisted**) or Google Drive. On Drive you must click
  **Share → General access → Anyone with the link → Viewer**, otherwise your marker gets an
  access request and the segment counts as missing. **Test every link in a private/incognito
  window before submitting.**
- **Note the timestamps** as you go — you need them for the report.

---

## Step 10 — Fill in the blanks in the report

Open `docs/stage2-report.md` in a text editor and search for `«FILL IN`. There are blanks in
four places:

| Where | What to put |
|---|---|
| Top of the document | Your repository URL, your name and student number |
| Section 5.1 | Which AI tools you used, which models, and the token and money cost |
| Section 5.2 | Run the CLI `usage` command after a session and paste what it prints |
| Section 5.3 | Two or three of your own real exchanges with an AI tool |
| Section 6.1 | Your commit counts and links for each period |
| Section 7 | Your video links and timestamps |

**For section 5.1**, be honest. If you used Claude on a subscription, say so and say the
subscription cost; subscriptions do not expose per-token figures and the marker knows that.
Where to find usage: Claude → Settings → Usage. ChatGPT → Settings → there is no per-token
view on Plus, so state the subscription. Copilot → your GitHub billing page.

**For section 5.3**, use your own real conversations. The three examples in the report show
the shape: what you asked, what came back, and **what you changed about it**. That last part
is what earns the marks — it shows you reviewed the output rather than pasting it.

### Then rebuild the PDF

```
cd docs
./build-report.sh
```

This regenerates `stage2-report.pdf` and `stage2-report.docx` in the project folder.

> **If `build-report.sh` fails** because pandoc is not installed, you can instead open
> `stage2-report.docx` in Word, make the same edits there, and save as PDF (File → Save As →
> PDF). Make sure the file is still named `stage2-report.pdf`.

Then commit it:

```
cd ..
git add -A
git commit -m "Fill in report details, video links and commit summary"
git push
```

---

## Step 11 — Final submission checklist

Go through this one line at a time before you submit.

- [ ] Repository is **public** (Settings → Change visibility)
- [ ] `.env` is **not** in the repository — only `.env.example`
- [ ] All source code is pushed (`src/`, `tools/`, `samples/`)
- [ ] `README.md` is there, and its instructions actually work on a fresh download
- [ ] `pom.xml` and the build scripts are there
- [ ] `stage2-report.pdf` is in the repository root and all `«FILL IN»` markers are gone
- [ ] Every feature has a video link with a timestamp in section 7
- [ ] Every video link opens in a **private browsing window** without an access request
- [ ] No API key is visible in any video, screenshot or committed file
- [ ] At least 5 commits in 3–9 November
- [ ] Repository URL submitted through the course submission system

---

## If something goes wrong

| What you see | What to do |
|---|---|
| `java: command not found` | Java is not installed, or you did not reopen the terminal after installing. Go back to Step 1. |
| `permission denied` running `./build.sh` | Type `chmod +x build.sh run.sh` then try again. |
| Yellow `OFFLINE MODE` warning | The key is not being read. Check `.env` is in the project folder, spelled exactly `.env`, with `LLM_API_KEY=gsk_...` and no spaces around `=`. |
| `The API key was rejected` | The key is wrong or was deleted. Make a new one at <https://console.groq.com>. |
| `Rate limit reached` | You are going too fast for the free tier. Wait a minute and carry on. |
| `The model name was not found` | The free model was renamed. Open the Groq console's model list, pick a current one, and put it in `.env` as `LLM_MODEL=...`. |
| Window does not open | Use the CLI instead: `./run.sh --cli`. |
| `git push` rejected | Try `git push -u origin main --force`. |
| GitHub asks for a password | It wants a personal access token, not your password. See Step 7d. |

---

## One last thing

You are responsible for this code, including the parts an AI wrote — that is what the Stage 2
instructions say, and it is fair. Before you submit, spend half an hour reading through
`README.md` and the report, and open a few source files. Every class has a comment at the top
explaining what it does and why it is built that way.

If a marker asks you "why did you use the Decorator pattern here?", you want to be able to
answer: *because retrying, caching, rate limiting and usage logging are four independent
concerns that apply to every model call, and putting them inside the transport class would
give it four reasons to change and duplicate all four in the offline provider.*

That answer is in section 3.9 of the report. Read it before your demo.
