#!/usr/bin/env bash
#
# Builds everything that comes from stage2-report.md:
#
#   ../stage2-report.pdf               the reference PDF (blanks shown as placeholders)
#   ../stage2-report.docx              a Word version, as a fallback
#   stage2-report-FILL-IN.html         the page you type your answers into
#
# Requires: pandoc and python3. A Chromium-based browser is used for the PDF when one
# is available, because it renders the diagrams as vectors; wkhtmltopdf is the fallback.
#
#   Ubuntu/Debian: sudo apt install pandoc
#   macOS:         brew install pandoc
#
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v pandoc >/dev/null 2>&1; then
    echo "ERROR: pandoc is not installed."
    echo "  Ubuntu/Debian: sudo apt install pandoc"
    echo "  macOS:         brew install pandoc"
    echo
    echo "You do not need this script to finish the report. Open"
    echo "docs/stage2-report-FILL-IN.html in your browser, fill it in, and print to PDF."
    exit 1
fi

# --------------------------------------------------------------------- diagrams

echo "Regenerating diagrams..."
if command -v python3 >/dev/null 2>&1; then
    (cd diagrams && python3 generate_diagrams.py >/dev/null)
fi
if command -v dot >/dev/null 2>&1; then
    (cd diagrams && dot -Tsvg class-diagram.dot -o class-diagram.svg 2>/dev/null || true)
    (cd diagrams && dot -Tpng -Gdpi=100 class-diagram.dot -o class-diagram.png 2>/dev/null || true)
fi
if command -v rsvg-convert >/dev/null 2>&1; then
    for name in use-case-diagram sd01-perform-code-analysis sd02-manage-review-finding \
                sd03-generate-export-report sd04-autonomous-audit; do
        [ -f "diagrams/$name.svg" ] && \
            rsvg-convert -z 1.6 -b white "diagrams/$name.svg" -o "diagrams/$name.png"
    done
fi

# --------------------------------------------------------------------- html

# Both pages come from one generator, so the reference PDF and the fill-in page can
# never drift apart. --plain shows the blanks as highlighted placeholders instead of
# making them editable, and leaves out the toolbar and the autosave script.
echo "Building the reference HTML..."
python3 make-fillable.py --plain >/dev/null

echo "Building the fill-in-the-blanks page..."
python3 make-fillable.py >/dev/null

# --------------------------------------------------------------------- pdf

echo "Building PDF..."

CHROME=""
for candidate in google-chrome google-chrome-stable chromium chromium-browser \
                 "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
                 "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"; do
    if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
        CHROME="$candidate"
        break
    fi
done

if [ -n "$CHROME" ]; then
    # A browser keeps the inlined SVG diagrams as vectors, so they stay sharp at any
    # zoom. wkhtmltopdf rasterises them down to page width, which is what made the
    # diagrams in the earlier build look blurry.
    "$CHROME" --headless=new --disable-gpu --no-sandbox \
        --no-pdf-header-footer \
        --print-to-pdf="$(pwd)/../stage2-report.pdf" \
        "file://$(pwd)/_report.html" >/dev/null 2>&1
    echo "  (rendered with $CHROME - diagrams are vector)"
elif command -v wkhtmltopdf >/dev/null 2>&1; then
    wkhtmltopdf \
        --enable-local-file-access \
        --page-size A4 \
        --margin-top 16mm --margin-bottom 16mm \
        --margin-left 14mm --margin-right 14mm \
        --image-quality 94 \
        _report.html ../stage2-report.pdf
    echo "  (rendered with wkhtmltopdf - install Chrome for sharper diagrams)"
else
    echo "  No browser or wkhtmltopdf found; using pandoc's PDF writer."
    pandoc stage2-report.md -o ../stage2-report.pdf
fi

# --------------------------------------------------------------------- docx

echo "Building DOCX..."
pandoc stage2-report.md \
    -f markdown \
    -t docx \
    --toc --toc-depth=2 \
    -o ../stage2-report.docx

rm -f _report.html

echo
echo "Done:"
ls -la ../stage2-report.pdf ../stage2-report.docx stage2-report-FILL-IN.html
