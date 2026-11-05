#!/usr/bin/env python3
"""Builds stage2-report-FILL-IN.html: a single self-contained, fill-in-the-blanks
version of the Stage 2 report.

Why this exists
---------------
The report has blanks that only the student can fill (repository URL, name, AI tool
costs, commit links, video links). Editing a PDF is painful and editing the Markdown
needs pandoc installed. This produces one HTML file that:

  * can be opened by double-clicking it, on any machine, with no software to install;
  * turns every blank into a highlighted box you type directly into;
  * remembers what you typed (browser local storage), so you can close and come back;
  * embeds the diagrams as vector SVG, so they stay sharp at any zoom - the blurriness
    in a wkhtmltopdf build comes from rasterising a very wide PNG down to page width;
  * prints to PDF through the browser with the highlighting and the instructions
    removed, so the result looks exactly like the finished report.

Usage:
    python3 make-fillable.py
"""

import base64
import html
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "stage2-report.md")
OUTPUT = os.path.join(HERE, "stage2-report-FILL-IN.html")
DIAGRAMS = os.path.join(HERE, "diagrams")


# --------------------------------------------------------------------------- markdown

def markdown_to_html(path):
    """Converts the report to an HTML fragment using pandoc."""
    try:
        return subprocess.run(
            ["pandoc", path, "-f", "markdown", "-t", "html5"],
            check=True, capture_output=True, text=True).stdout
    except FileNotFoundError:
        sys.exit("ERROR: pandoc is not installed.\n"
                 "  Ubuntu/Debian: sudo apt install pandoc\n"
                 "  macOS:         brew install pandoc")
    except subprocess.CalledProcessError as error:
        sys.exit("ERROR: pandoc failed:\n" + error.stderr)


# --------------------------------------------------------------------------- diagrams

def inline_diagrams(markup):
    """Replaces <img src="diagrams/x.png"> with the matching SVG, inlined.

    Inlining keeps the diagrams as vectors, so they print sharp instead of being
    rasterised. Element ids are namespaced per file because several of the generated
    SVGs declare the same marker ids, and duplicate ids in one document make every
    arrowhead point at the first definition.
    """
    counter = {"n": 0}

    def replace(match):
        name = match.group(1)
        svg_path = os.path.join(DIAGRAMS, name + ".svg")
        if not os.path.exists(svg_path):
            return match.group(0)

        counter["n"] += 1
        prefix = "d%d_" % counter["n"]
        with open(svg_path, encoding="utf-8") as handle:
            svg = handle.read()

        # Strip any XML prolog or doctype; keep from the opening <svg tag.
        start = svg.find("<svg")
        if start > 0:
            svg = svg[start:]

        # Namespace ids so the diagrams cannot interfere with each other.
        ids = set(re.findall(r'id="([^"]+)"', svg))
        for identifier in ids:
            svg = svg.replace('id="%s"' % identifier, 'id="%s%s"' % (prefix, identifier))
            svg = svg.replace('url(#%s)' % identifier, 'url(#%s%s)' % (prefix, identifier))
            svg = svg.replace('xlink:href="#%s"' % identifier,
                              'xlink:href="#%s%s"' % (prefix, identifier))

        # Let the page size it: drop fixed pixel dimensions, keep the viewBox.
        svg = re.sub(r'<svg([^>]*?)\swidth="[^"]*"', r'<svg\1', svg, count=1)
        svg = re.sub(r'<svg([^>]*?)\sheight="[^"]*"', r'<svg\1', svg, count=1)
        svg = svg.replace("<svg", '<svg class="diagram" preserveAspectRatio="xMidYMid meet"', 1)

        # The class diagram is far wider than it is tall, so at page width it becomes
        # unreadable. It gets its own page, rotated, where it can fill the sheet.
        extra = " landscape" if name == "class-diagram" else ""
        return '<figure class="diagram-wrap%s">%s</figure>' % (extra, svg)

    markup = re.sub(r'<img src="diagrams/([^."]+)\.png"[^>]*/?>', replace, markup)
    # pandoc turns the image's alt text into a caption, which duplicates the explicit
    # "Figure N - ..." line already written under each diagram in the report.
    return re.sub(r'<figcaption[^>]*>.*?</figcaption>', '', markup, flags=re.DOTALL)


# --------------------------------------------------------------------------- blanks

#: A long marker is guidance for the student, not something that belongs in the
#: finished report, so it is rendered as a note that disappears when printing.
INSTRUCTION_THRESHOLD = 60

BLANK_PATTERN = re.compile(r'«(.*?)»', re.DOTALL)


def convert_blanks(markup, editable=True):
    """Turns every «...» marker into either a field or a printed-out note.

    With editable=False the blanks are rendered as visible highlighted placeholders
    instead of input boxes, which is what the reference PDF in the repository shows:
    a reader can see exactly which parts are still to be completed.
    """
    counter = {"fields": 0, "notes": 0}

    def replace(match):
        raw = match.group(1)
        text = html.unescape(raw).replace("\n", " ").strip()
        text = re.sub(r"\s+", " ", text)

        if len(text) > INSTRUCTION_THRESHOLD:
            counter["notes"] += 1
            return ('<span class="guide" data-guide="%d">%s</span>'
                    % (counter["notes"], html.escape(text)))

        counter["fields"] += 1
        index = counter["fields"]
        hint = html.escape(text)
        if not editable:
            return '<span class="blank-plain">%s</span>' % hint
        return ('<span class="blank" contenteditable="true" spellcheck="false" '
                'data-blank="b%d" data-hint="%s"></span>' % (index, hint))

    converted = BLANK_PATTERN.sub(replace, markup)
    return converted, counter["fields"], counter["notes"]


# --------------------------------------------------------------------------- page

def load_css():
    path = os.path.join(HERE, "report.css")
    if not os.path.exists(path):
        return ""
    with open(path, encoding="utf-8") as handle:
        return handle.read()


EXTRA_CSS = """
/* ----------------------------------------------------------- screen chrome */
body { max-width: 980px; margin: 0 auto; padding: 0 26px 80px; }

#toolbar {
    position: sticky; top: 0; z-index: 50;
    background: #1f2b3d; color: #fff;
    margin: 0 -26px 26px; padding: 12px 26px;
    display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
    box-shadow: 0 2px 10px rgba(0,0,0,.18);
}
#toolbar h2 { margin: 0; font-size: 13.5pt; color: #fff; border: none; padding: 0; }
#toolbar .spacer { flex: 1; }
#toolbar button {
    font: inherit; font-size: 10.5pt; font-weight: 600;
    background: #2d6cdf; color: #fff; border: none;
    padding: 8px 16px; border-radius: 5px; cursor: pointer;
}
#toolbar button:hover { background: #255bbd; }
#toolbar button.ghost { background: #3a4a61; }
#toolbar button.ghost:hover { background: #4a5c76; }
#progress { font-size: 10.5pt; background: #33425a; padding: 6px 12px; border-radius: 20px; }
#progress.done { background: #2e6d33; }

#howto {
    background: #fff8e6; border: 1px solid #e0c56a; border-left: 4px solid #d9a521;
    border-radius: 6px; padding: 16px 20px; margin-bottom: 30px; font-size: 10.4pt;
}
#howto h3 { margin-top: 0; color: #8a6400; }
#howto ol { margin-bottom: 0; }

/* ----------------------------------------------------------- editable blanks */
.blank {
    display: inline-block; min-width: 90px;
    background: #fff4c2; border-bottom: 2px solid #d9a521; border-radius: 3px;
    padding: 1px 6px; outline: none; cursor: text;
    font-family: inherit; color: #1c1f23;
}
.blank:hover { background: #ffecA0; }
.blank:focus { background: #fff; box-shadow: 0 0 0 2px #2d6cdf; border-bottom-color: #2d6cdf; }
.blank:empty::before {
    content: attr(data-hint); color: #a08436; font-style: italic;
}
.blank.filled { background: #e7f3e8; border-bottom-color: #4a9150; }
.blank.filled:hover { background: #dcecde; }

/* Guidance that must not reach the finished report. */
.guide {
    display: block; background: #eef4ff; border-left: 3px solid #2d6cdf;
    border-radius: 4px; padding: 9px 13px; margin: 10px 0;
    font-size: 9.6pt; color: #27456f; font-style: italic;
}
.guide::before {
    content: "INSTRUCTIONS (will not appear in your PDF) - ";
    font-weight: 700; font-style: normal; color: #2d6cdf;
}

/* Visible placeholder used by the reference (non-editable) build. */
.blank-plain {
    background: #fff4c2; border-bottom: 1px solid #d9a521; border-radius: 3px;
    padding: 0 5px; color: #7a5c0e; font-weight: 600;
}

/* ----------------------------------------------------------- diagrams */
.diagram-wrap { margin: 16px 0; padding: 0; text-align: center; }
svg.diagram { width: 100%; height: auto; max-width: 100%; border: 1px solid #dde2e8; border-radius: 4px; background: #fff; }

/* ----------------------------------------------------------- print */
@media print {
    @page { size: A4; margin: 15mm 13mm; }

    #toolbar, #howto, .guide { display: none !important; }

    body { max-width: none; margin: 0; padding: 0; font-size: 9.6pt; }

    .blank {
        background: none !important; border: none !important;
        box-shadow: none !important; padding: 0; min-width: 0;
        color: #1c1f23; display: inline;
    }
    /* Anything still blank prints in red, so it cannot be missed. */
    .blank:empty::before {
        content: "[ STILL BLANK ]"; color: #c62828; font-style: normal; font-weight: 700;
    }

    h1, h2, h3, h4 { page-break-after: avoid; }
    /* Diagrams and code blocks stay whole; a long table may break across pages,
       but never in the middle of a row, and its header repeats. */
    pre, .diagram-wrap, figure { page-break-inside: avoid; }
    table { page-break-inside: auto; }
    tr    { page-break-inside: avoid; }
    thead { display: table-header-group; }
    svg.diagram { border: 1px solid #dde2e8; max-height: 238mm; }

    /* A very wide diagram gets a page of its own, turned sideways so it fills it. */
    .diagram-wrap.landscape {
        page-break-before: always;
        page-break-after: always;
        height: 255mm;
        position: relative;
        overflow: visible;
    }
    /* Positioned absolutely rather than laid out in flow: a rotated element keeps its
       unrotated layout box, so in normal flow the browser would shrink it to fit the
       page width before the rotation ever happened. */
    .diagram-wrap.landscape svg.diagram {
        position: absolute;
        top: 50%;
        left: 50%;
        width: 252mm;
        max-width: none;
        max-height: none;
        transform: translate(-50%, -50%) rotate(90deg);
    }
    a { color: inherit; text-decoration: none; }
}
"""

TOOLBAR = """
<div id="toolbar">
  <h2>Stage 2 Report &mdash; fill in the blanks</h2>
  <span id="progress">0 of 0 filled</span>
  <span class="spacer"></span>
  <button onclick="jumpToNextBlank()" class="ghost">Next blank</button>
  <button onclick="window.print()">Save as PDF</button>
  <button onclick="clearAll()" class="ghost">Clear all</button>
</div>

<div id="howto">
  <h3>How to use this page</h3>
  <ol>
    <li>Every <strong>yellow box</strong> is something you need to type in. Click it and type.
        It turns green once filled.</li>
    <li>Press <strong>Next blank</strong> to jump to the next one you have not done.</li>
    <li>Your typing is saved in this browser automatically. You can close the page and
        come back to it.</li>
    <li>Blue <strong>INSTRUCTIONS</strong> boxes explain what to put where. They are
        guidance only and will <strong>not</strong> appear in your PDF.</li>
    <li>When every box is green, press <strong>Save as PDF</strong>. In the print dialog
        choose <em>Destination: Save as PDF</em>, set <em>Margins: Default</em>, and tick
        <em>Background graphics</em>. Save it as <code>stage2-report.pdf</code> and put it
        in the top level of your repository, replacing the old one.</li>
  </ol>
  <p style="margin-bottom:0"><strong>Use Chrome or Edge</strong> for the best result.
  Safari and Firefox work, but Chrome produces the most faithful page breaks.</p>
</div>
"""

SCRIPT = """
<script>
(function () {
    var KEY = 'eecs3311-stage2-report-blanks';

    function allBlanks() {
        return Array.prototype.slice.call(document.querySelectorAll('.blank'));
    }

    function load() {
        var saved;
        try { saved = JSON.parse(localStorage.getItem(KEY) || '{}'); }
        catch (e) { saved = {}; }
        allBlanks().forEach(function (node) {
            var value = saved[node.dataset.blank];
            if (value) { node.textContent = value; }
        });
    }

    function save() {
        var data = {};
        allBlanks().forEach(function (node) {
            var value = node.textContent.trim();
            if (value) { data[node.dataset.blank] = value; }
        });
        try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) { /* private mode */ }
    }

    function refresh() {
        var blanks = allBlanks();
        var filled = 0;
        blanks.forEach(function (node) {
            var has = node.textContent.trim().length > 0;
            node.classList.toggle('filled', has);
            if (has) { filled++; }
        });
        var progress = document.getElementById('progress');
        progress.textContent = filled + ' of ' + blanks.length + ' filled';
        progress.classList.toggle('done', filled === blanks.length);
    }

    window.jumpToNextBlank = function () {
        var next = allBlanks().filter(function (node) {
            return node.textContent.trim().length === 0;
        })[0];
        if (!next) { alert('Every blank is filled. You can press "Save as PDF" now.'); return; }
        next.scrollIntoView({ behavior: 'smooth', block: 'center' });
        setTimeout(function () { next.focus(); }, 300);
    };

    window.clearAll = function () {
        if (!confirm('Erase everything you have typed on this page?')) { return; }
        try { localStorage.removeItem(KEY); } catch (e) { /* ignore */ }
        allBlanks().forEach(function (node) { node.textContent = ''; });
        refresh();
    };

    document.addEventListener('input', function (event) {
        if (event.target.classList && event.target.classList.contains('blank')) {
            save();
            refresh();
        }
    });

    // Keep pasted content as plain text, so a copied link cannot drag in styling.
    document.addEventListener('paste', function (event) {
        if (!event.target.classList || !event.target.classList.contains('blank')) { return; }
        event.preventDefault();
        var text = (event.clipboardData || window.clipboardData).getData('text');
        document.execCommand('insertText', false, text.replace(/\\s+/g, ' ').trim());
    });

    // Enter should move on, not insert a line break inside a table cell.
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && event.target.classList
                && event.target.classList.contains('blank')) {
            event.preventDefault();
            event.target.blur();
            window.jumpToNextBlank();
        }
    });

    load();
    refresh();
})();
</script>
"""


PLAIN_OUTPUT = os.path.join(HERE, "_report.html")


def build(editable=True):
    markup = markdown_to_html(SOURCE)
    markup = inline_diagrams(markup)
    markup, field_count, note_count = convert_blanks(markup, editable=editable)

    title = ("Stage 2 Implementation Report - fill in the blanks" if editable
             else "Stage 2 Implementation Report")
    destination = OUTPUT if editable else PLAIN_OUTPUT

    page = (
        '<!DOCTYPE html>\n<html lang="en">\n<head>\n'
        '<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<title>' + title + '</title>\n'
        '<style>\n' + load_css() + EXTRA_CSS + '</style>\n'
        '</head>\n<body>\n'
        + (TOOLBAR if editable else "")
        + markup
        + (SCRIPT if editable else "")
        + '\n</body>\n</html>\n'
    )

    with open(destination, "w", encoding="utf-8") as handle:
        handle.write(page)

    size = os.path.getsize(destination) / 1024.0
    print("Wrote %s" % destination)
    if editable:
        print("  %d editable blanks" % field_count)
        print("  %d instruction notes (hidden when printing)" % note_count)
    else:
        print("  %d placeholders shown, %d instruction notes" % (field_count, note_count))
    print("  %.0f KB, fully self-contained - no other files needed" % size)


if __name__ == "__main__":
    build(editable="--plain" not in sys.argv)
