#!/usr/bin/env python3
"""Builds stage2-report-FILLABLE.html from stage2-report.md.

Why this exists
---------------
stage2-report.pdf is generated output. Editing a PDF directly - especially by opening it
in Word, which converts it back into a document - destroys the layout, blurs the images and
breaks the tables. This script produces a single self-contained HTML file instead:

  * every «FILL IN» marker becomes a yellow box you type into, in your browser,
  * every image is embedded in the file, so it works offline and keeps full resolution,
  * your answers are saved in the browser as you type, so you cannot lose them,
  * printing it with Chrome's "Save as PDF" reproduces the report exactly, and sharper
    than the original build, because the browser embeds the diagrams at full resolution.

Usage:
    python3 make-fillable.py

Requires pandoc. Everything else is standard library.
"""

import base64
import html
import json
import mimetypes
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MARKDOWN = os.path.join(HERE, "stage2-report.md")
CSS = os.path.join(HERE, "report.css")
OUTPUT = os.path.join(HERE, "stage2-report-FILLABLE.html")


def run_pandoc():
    """Converts the Markdown report to an HTML fragment."""
    try:
        return subprocess.run(
            ["pandoc", MARKDOWN, "-f", "markdown", "-t", "html5"],
            check=True, capture_output=True, text=True).stdout
    except FileNotFoundError:
        sys.exit("ERROR: pandoc is not installed.\n"
                 "  Ubuntu/Debian: sudo apt install pandoc\n"
                 "  macOS:         brew install pandoc\n"
                 "  Windows:       https://pandoc.org/installing.html")
    except subprocess.CalledProcessError as error:
        sys.exit("ERROR: pandoc failed:\n" + error.stderr)


def embed_images(markup):
    """Replaces every <img src="diagrams/x.png"> with an inline base64 data URI.

    This makes the output a single file that can be emailed or moved anywhere, and keeps
    the diagrams at their original resolution so they stay sharp when zoomed in the PDF.
    """
    def replace(match):
        src = match.group(1)
        path = os.path.join(HERE, src)
        if not os.path.isfile(path):
            print("  warning: image not found, left as a link:", src)
            return match.group(0)
        mime = mimetypes.guess_type(path)[0] or "image/png"
        with open(path, "rb") as handle:
            data = base64.b64encode(handle.read()).decode("ascii")
        print("  embedded", src, f"({os.path.getsize(path) // 1024} KB)")
        tag = match.group(0).replace(f'src="{src}"', f'src="data:{mime};base64,{data}"')
        # The class diagram is far wider than it is tall. Mark it so the print rules can
        # give it a landscape page of its own, where it is actually readable.
        if "class-diagram" in src:
            tag = tag.replace("<img", '<img class="wide-figure"', 1)
        return tag

    return re.sub(r'<img[^>]*src="([^"]+)"[^>]*>', replace, markup)


FIELD_PATTERN = re.compile(r"«([^»]*)»")


def make_fields(markup):
    """Turns every «...» marker into an editable field and returns the count."""
    counter = {"n": 0}

    def replace(match):
        counter["n"] += 1
        index = counter["n"]
        # pandoc has already HTML-escaped the marker text; unescape for the hint, then
        # re-escape it for the attribute so quotes inside a hint cannot break the tag.
        raw = html.unescape(match.group(1)).strip()
        hint = raw
        for prefix in ("FILL IN:", "FILL IN"):
            if hint.upper().startswith(prefix):
                hint = hint[len(prefix):].lstrip(": ").strip()
                break
        if not hint:
            hint = "type your answer here"
        short = hint if len(hint) <= 90 else hint[:88] + "..."
        return (f'<span class="fillin" id="f{index}" data-field="{index}" '
                f'contenteditable="true" spellcheck="true" '
                f'data-hint="{html.escape(short, quote=True)}"></span>')

    return FIELD_PATTERN.sub(replace, markup), counter["n"]


def build(markup, field_count, stylesheet):
    """Wraps the processed body in a full document with the toolbar and print rules."""
    return TEMPLATE.replace("/*STYLESHEET*/", stylesheet) \
                   .replace("<!--BODY-->", markup) \
                   .replace("__FIELD_COUNT__", str(field_count))


TEMPLATE = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Stage 2 Implementation Report - fill in and print</title>
<style>
/*STYLESHEET*/

/* ------------------------------------------------------------------ screen layout */
body.fillable {
    background: #e9ecf1;
    padding: 0;
}
#sheet {
    background: #ffffff;
    max-width: 210mm;
    margin: 26px auto 60px;
    padding: 20mm 16mm;
    box-shadow: 0 2px 18px rgba(0, 0, 0, 0.14);
}

/* ------------------------------------------------------------------ toolbar */
#toolbar {
    position: sticky;
    top: 0;
    z-index: 50;
    background: #1f2937;
    color: #f3f5f8;
    padding: 10px 16px;
    font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-size: 13px;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.22);
}
#toolbar strong { color: #ffffff; }
#toolbar button {
    background: #2d6cdf;
    color: #ffffff;
    border: none;
    border-radius: 5px;
    padding: 6px 12px;
    font-size: 12.5px;
    font-weight: 600;
    cursor: pointer;
    font-family: inherit;
}
#toolbar button:hover { background: #2559b8; }
#toolbar button.ghost { background: #3b4756; }
#toolbar button.ghost:hover { background: #4a5767; }
#remaining {
    background: #b31c1c;
    border-radius: 11px;
    padding: 3px 11px;
    font-weight: 700;
}
#remaining.done { background: #2e6d33; }
#saved {
    color: #9fb0c4;
    font-size: 12px;
    margin-left: auto;
}

#helpbar {
    background: #fff8e6;
    border-bottom: 1px solid #e6d9ae;
    color: #6b5a2e;
    padding: 9px 16px;
    font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-size: 12.5px;
    line-height: 1.5;
}
#helpbar b { color: #4d4120; }

/* pandoc renders alt text as a <figcaption>; the report already writes its own italic
   caption under every figure, so the generated one would be a duplicate. */
figure { margin: 12px 0; }
figcaption { display: none; }

/* ------------------------------------------------------------------ fields */
.fillin {
    display: inline-block;
    min-width: 150px;
    background: #fff3b0;
    border: 1px solid #d9b45b;
    border-radius: 3px;
    padding: 1px 6px;
    outline: none;
    font-style: normal;
    color: #1c1f23;
}
.fillin:empty::before {
    content: attr(data-hint);
    color: #9a7f36;
    font-style: italic;
}
/* Inside a preformatted block a wide placeholder would stretch the block, so the fields
   there stay narrow. */
pre .fillin { min-width: 56px; }
pre .fillin:empty::before { content: "..."; }

.fillin:focus {
    background: #fffceb;
    border-color: #2d6cdf;
    box-shadow: 0 0 0 2px rgba(45, 108, 223, 0.25);
}
.fillin.filled {
    background: #e8f4e9;
    border-color: #9cc3a1;
}
.fillin.flash {
    animation: flash 1.1s ease-out;
}
@keyframes flash {
    0%   { box-shadow: 0 0 0 6px rgba(45, 108, 223, 0.55); }
    100% { box-shadow: 0 0 0 0 rgba(45, 108, 223, 0); }
}

/* ------------------------------------------------------------------ printing */
@media print {
    #toolbar, #helpbar, #printnote { display: none !important; }

    /* The class diagram is wide; keep it whole and let it fill the text column. It is
       embedded at full resolution, so it stays sharp when zoomed, and the full-size file
       is also in docs/diagrams/ for anyone who wants to open it separately. */
    .wide-figure-page { page-break-inside: avoid; text-align: center; }
    .wide-figure-page img { max-width: 100%; }

    h1 { page-break-after: avoid; }
    h2, h3, h4 { page-break-after: avoid; }
    /* Tables may split across pages - forcing them whole leaves large blank gaps.
       report.css already keeps individual rows intact and repeats the header row. */
    pre, img, blockquote { page-break-inside: avoid; }
    table { page-break-inside: auto; }
    /* Make Chrome keep the table shading and the coloured headings. */
    * {
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
    }
}

#printnote {
    max-width: 210mm;
    margin: 0 auto 10px;
    padding: 12px 16px;
    background: #eaf0fa;
    border: 1px solid #c3d2ea;
    border-radius: 6px;
    font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-size: 12.5px;
    color: #23395e;
    line-height: 1.55;
}
#printnote ol { margin: 6px 0 0 18px; padding: 0; }
#printnote li { margin: 3px 0; }
</style>
</head>
<body class="fillable">

<div id="toolbar">
    <strong>Stage 2 report</strong>
    <span id="remaining">? blanks left</span>
    <button onclick="nextBlank()">Jump to next blank</button>
    <button class="ghost" onclick="window.print()">Save as PDF</button>
    <button class="ghost" onclick="exportAnswers()">Back up answers</button>
    <button class="ghost" onclick="document.getElementById('importer').click()">Restore answers</button>
    <input type="file" id="importer" accept=".json" style="display:none" onchange="importAnswers(event)">
    <span id="saved">Saved automatically in this browser</span>
</div>

<div id="helpbar">
    <b>How to use this:</b> click any yellow box and type. Your answers save automatically as
    you type, in this browser. When every box is filled, press <b>Save as PDF</b>, choose
    <b>Destination: Save as PDF</b>, turn <b>Background graphics ON</b> and
    <b>Headers and footers OFF</b>, then save the file as <b>stage2-report.pdf</b>.
    The yellow highlighting does not appear in the PDF.
</div>

<div id="printnote">
    <b>Before you print:</b>
    <ol>
        <li>The counter above must say <b>0 blanks left</b>. Anything you miss prints as a red
            <b>[ TO BE COMPLETED ]</b> so you cannot submit it by accident.</li>
        <li>In the print dialog: <b>Destination</b> = Save as PDF, <b>Paper size</b> = A4,
            <b>Margins</b> = Default, <b>Background graphics</b> = ticked,
            <b>Headers and footers</b> = unticked.</li>
        <li>Save it over the old <code>stage2-report.pdf</code> in your project folder, then
            commit and push.</li>
    </ol>
</div>

<div id="sheet">
<!--BODY-->
</div>

<script>
const TOTAL = __FIELD_COUNT__;
const STORAGE_KEY = "eecs3311-stage2-report-answers";

function fields() {
    return Array.from(document.querySelectorAll(".fillin"));
}

function save() {
    const data = {};
    fields().forEach(function (field) {
        const value = field.innerText.trim();
        if (value) { data[field.dataset.field] = value; }
    });
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        document.getElementById("saved").textContent =
            "Saved " + new Date().toLocaleTimeString();
    } catch (e) {
        document.getElementById("saved").textContent =
            "Could not save automatically - use 'Back up answers'";
    }
}

function load() {
    let data = {};
    try {
        data = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    } catch (e) { data = {}; }
    apply(data);
}

function apply(data) {
    fields().forEach(function (field) {
        const value = data[field.dataset.field];
        if (value) { field.innerText = value; }
    });
    refresh();
}

function refresh() {
    let empty = 0;
    fields().forEach(function (field) {
        const filled = field.innerText.trim().length > 0;
        field.classList.toggle("filled", filled);
        if (!filled) { empty++; }
    });
    const badge = document.getElementById("remaining");
    badge.textContent = empty === 0
        ? "All " + TOTAL + " blanks filled"
        : empty + " of " + TOTAL + " blanks left";
    badge.classList.toggle("done", empty === 0);
}

function nextBlank() {
    const blank = fields().find(function (field) {
        return field.innerText.trim().length === 0;
    });
    if (!blank) {
        alert("Every blank is filled. You can press 'Save as PDF' now.");
        return;
    }
    blank.scrollIntoView({ behavior: "smooth", block: "center" });
    blank.classList.remove("flash");
    void blank.offsetWidth;
    blank.classList.add("flash");
    setTimeout(function () { blank.focus(); }, 320);
}

function exportAnswers() {
    const data = {};
    fields().forEach(function (field) {
        const value = field.innerText.trim();
        if (value) { data[field.dataset.field] = value; }
    });
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "stage2-report-answers.json";
    link.click();
}

function importAnswers(event) {
    const file = event.target.files[0];
    if (!file) { return; }
    const reader = new FileReader();
    reader.onload = function () {
        try {
            apply(JSON.parse(reader.result));
            save();
        } catch (e) {
            alert("That file could not be read as a saved answer file.");
        }
    };
    reader.readAsText(file);
}

document.addEventListener("input", function (event) {
    if (event.target.classList && event.target.classList.contains("fillin")) {
        refresh();
        save();
    }
});

// Paste as plain text, so pasting a link out of a browser cannot drag styling in with it.
document.addEventListener("paste", function (event) {
    if (!event.target.classList || !event.target.classList.contains("fillin")) { return; }
    event.preventDefault();
    const text = (event.clipboardData || window.clipboardData).getData("text");
    document.execCommand("insertText", false, text.replace(/\s+/g, " ").trim());
});

// Enter should not create new lines inside a one-line field.
document.addEventListener("keydown", function (event) {
    if (event.key === "Enter" && event.target.classList
            && event.target.classList.contains("fillin")) {
        event.preventDefault();
        event.target.blur();
    }
});

load();
refresh();
</script>
</body>
</html>
"""


def main():
    print("Converting Markdown to HTML...")
    markup = run_pandoc()

    print("Embedding diagrams...")
    markup = embed_images(markup)

    print("Isolating the wide class diagram...")
    markup = re.sub(r"<p>(<img class=\"wide-figure\"[^>]*>)</p>",
                    r'<div class="wide-figure-page">\1</div>', markup)
    markup = re.sub(r"<figure>\s*(<img class=\"wide-figure\"[^>]*>).*?</figure>",
                    r'<div class="wide-figure-page">\1</div>', markup, flags=re.S)

    print("Creating fill-in fields...")
    markup, count = make_fields(markup)
    print(f"  {count} fields created")

    with open(CSS, encoding="utf-8") as handle:
        stylesheet = handle.read()

    with open(OUTPUT, "w", encoding="utf-8") as handle:
        handle.write(build(markup, count, stylesheet))

    size = os.path.getsize(OUTPUT) // 1024
    print(f"\nWrote {OUTPUT} ({size} KB, self-contained)")
    print("Open it in Chrome or Edge, fill in the yellow boxes, then press Save as PDF.")


if __name__ == "__main__":
    main()
