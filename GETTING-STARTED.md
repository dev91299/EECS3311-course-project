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
| `setup-key.sh` / `setup-key.bat` | Sets up your API key and checks it works |
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

**There is a script that does all of this for you.** Use it — it avoids the hidden-file
problem entirely, and it checks your key actually works before you go any further.

### The easy way

In your terminal, in the project folder, type:

**Mac or Linux:**

```
./setup-key.sh
```

**Windows:** type `setup-key.bat`, or just **double-click `setup-key.bat`** in File Explorer.

It will:

1. Create the configuration file for you.
2. Ask you to paste your key.
3. Save it.
4. Check Git is ignoring it, so you can never commit it by accident.
5. **Make a real request to the AI and show you the reply**, so you know it works.

When it finishes you will see:

```
[OK] Git is ignoring .env, so your key can never be committed by accident.

Checking that the key works...

[OK] The key works. Here is the model's reply to a real request:

     This class calculates line totals, applying a bulk discount and then tax...

==============================================
  Setup complete.
```

**That's Step 5 done.** Skip to Step 6.

> **On Mac/Linux, nothing appears as you paste your key.** That is deliberate — the key is
> hidden so it cannot be read off your screen or captured in a screen recording. Paste it and
> press Enter even though the line looks empty.

> **If you get `permission denied`:** type `chmod +x setup-key.sh` first, then try again.

---

### If the script tells you something is wrong

| What it says | What it means |
|---|---|
| `[OK] The key works` | Done. Move on to Step 6. |
| `[??] Could not reach the provider` | **Your key is fine — don't regenerate it.** This is a connection problem. Check you are online. School and work networks often block outside sites; try a home network or a phone hotspot. |
| `[!!] The provider rejected the key` | The key is mistyped or was deleted. Make a new one at <https://console.groq.com> and run the script again. |
| `[!!] still in OFFLINE MODE` | The file was saved but is not being read. Make sure you are running the script from inside the project folder. |
| `[!!] the model it asked for no longer exists` | **Your key is fine.** Providers retire model IDs every few months. The script switches you to a current one automatically and re-checks. |

---

### The manual way (only if the script will not run)

The file is called `.env` — with a dot at the start. **That dot makes it invisible in Finder
and File Explorer by default, which is almost certainly why you could not find it.**

**Step A — create it.** In the terminal, in the project folder:

Mac or Linux:
```
cp .env.example .env
```

Windows:
```
copy .env.example .env
```

**Step B — confirm it exists.** The terminal can see hidden files even when your file manager
cannot:

Mac or Linux:
```
ls -a
```

Windows:
```
dir /a
```

You should see `.env` in the list. If it is there, the file exists — your file manager is
just hiding it.

**Step C — open it.** The simplest way is to tell your computer to open it, rather than
hunting for it:

Mac:
```
open -e .env
```

Linux:
```
xdg-open .env
```

Windows:
```
notepad .env
```

> **To make hidden files visible instead:** on Mac, press `Cmd + Shift + .` in Finder.
> On Windows, in File Explorer go to **View → Show → Hidden items**.

**Step D — edit it.** Find this line:

```
LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE
```

Replace the placeholder with your real key, so it reads:

```
LLM_API_KEY=gsk_abc123yourrealkeyhere
```

No spaces around the `=`, no quotes around the key. Save and close.

**Step E — check it works:**

```
./run.sh --cli --repo samples/demo-project --command "summarize PriceCalculator.java"
```

(Windows: `run.bat --cli --repo samples\demo-project --command "summarize PriceCalculator.java"`)

If you see a proper description of what the class does, it works. If you see
`OFFLINE STRUCTURAL SUMMARY`, the key is not being read — go back to Step D and check the
line carefully.

---

### Keeping your key safe

Run this:

```
git status
```

`.env` must **not** appear anywhere in the output. The setup script checks this for you
automatically.

> **Why this matters:** an API key is like a password. If you push it to a public GitHub
> repository, anyone can use it, and the course explicitly penalises this. The project is set
> up so `.env` can never be committed by accident.
>
> **If you ever do leak a key:** go to the Groq console and delete it immediately, then make
> a new one. Deleting the file afterwards is not enough, because the key stays in the Git
> history.

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

**Do not try to edit the PDF, and do not open it in a text editor** — a PDF is not a text
file, so a text editor shows you the raw compressed data, which is why it looked like
nonsense.

Instead there is a page built for exactly this job.

### 10a. Open the fill-in page

In your file manager, go to the `docs` folder and **double-click
`stage2-report-FILL-IN.html`**. It opens in your web browser.

> Use **Chrome** or **Edge** if you have one. Safari and Firefox work, but Chrome gives the
> most faithful page breaks when you save the PDF.

You will see the whole report, with a dark blue bar across the top that says
**"0 of 76 filled"**.

### 10b. Fill in the yellow boxes

- Every **yellow box** is something you type in. Click it and type. It turns **green** once
  it has something in it.
- The counter at the top tells you how many are left.
- Press **Next blank** to jump straight to the next one you have not done.
- **Blue INSTRUCTIONS boxes** explain what goes where. They are guidance only and will
  **not** appear in your finished PDF.
- Your typing is saved automatically in the browser. You can close the page and come back
  later and it will all still be there.

What the 76 blanks are, roughly:

| Where | What to put |
|---|---|
| Top of the report | Your repository URL, your name and student number |
| Section 5.1 | The AI tools you used, the models, and the token and money cost |
| Section 5.2 | Run the CLI `usage` command after a session and copy the numbers in |
| Section 5.3 | Two or three of your own real exchanges with an AI tool |
| Section 6.1 | Your commit counts and links for each period |
| Section 7 | Your video links and timestamps (two boxes per feature) |

**For section 5.1**, be honest. If you used Claude or ChatGPT on a monthly subscription, say
so and give the subscription cost — subscriptions do not show per-token figures and the
marker knows that. Where to look: Claude → Settings → Usage. Copilot → your GitHub billing
page.

**For section 5.3**, use your own real conversations. The three examples already written in
the report show the shape: what you asked, what came back, and **what you changed about it**.
That last part is what earns the marks, because it shows you reviewed the output rather than
pasting it.

### 10c. Save it as a PDF

When the counter says **"76 of 76 filled"** and has turned green:

1. Press the blue **Save as PDF** button at the top (or just press `Ctrl+P`, `Cmd+P` on Mac).
2. In the dialog that opens, set:
   - **Destination:** Save as PDF
   - **Margins:** Default
   - **Background graphics:** ticked (this is under "More settings")
3. Click **Save**, name the file **`stage2-report.pdf`**, and save it into the **top level of
   the project folder**, replacing the old one.

The yellow highlighting, the blue instruction boxes and the toolbar all disappear
automatically — the saved PDF looks like a finished report.

> **Safety net:** if you miss a box, it prints as a red **[ STILL BLANK ]** rather than
> silently disappearing, so flick through the PDF once before you submit.

### 10d. Commit it

```
git add -A
git commit -m "Fill in report details, video links and commit summary"
git push
```

### If you would rather use Word

`stage2-report.docx` is there as a fallback. Open it in Word, use **Find** (`Ctrl+F`) to
search for `FILL IN`, replace each one, then **File → Save As → PDF** and name it
`stage2-report.pdf`. The HTML method gives a better-looking result, but this works.

### If you change the report's wording

Only if you want to edit the actual text rather than just the blanks: edit
`docs/stage2-report.md` in a text editor (that one **is** a plain text file), then run
`cd docs && ./build-report.sh` to regenerate everything. This needs `pandoc` installed. You
do not need to do this just to fill in the blanks.

## Step 11 — Final submission checklist

Go through this one line at a time before you submit.

- [ ] Repository is **public** (Settings → Change visibility)
- [ ] `.env` is **not** in the repository — only `.env.example`
- [ ] All source code is pushed (`src/`, `tools/`, `samples/`)
- [ ] `README.md` is there, and its instructions actually work on a fresh download
- [ ] `pom.xml` and the build scripts are there
- [ ] `stage2-report.pdf` is in the repository root, and flicking through it shows no
      yellow placeholders and no red `[ STILL BLANK ]` text
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
| Yellow `OFFLINE MODE` warning | The key is not being read. Run `./setup-key.sh` (Windows: `setup-key.bat`) and it will diagnose it for you. |
| Cannot find the `.env` file | It starts with a dot, so file managers hide it. Run `./setup-key.sh` instead — you never need to find the file. |
| `The API key was rejected` | The key is wrong or was deleted. Make a new one at <https://console.groq.com>. |
| `Rate limit reached` | You are going too fast for the free tier. Wait a minute and carry on. |
| `The model name was not found` | That model ID was retired. Run `./setup-key.sh` and it switches you to a current one, or pick one from <https://console.groq.com/docs/models> and set `LLM_MODEL=` in `.env`. |
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
