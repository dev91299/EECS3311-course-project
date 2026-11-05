#!/usr/bin/env bash
#
# Builds stage2-report.pdf and stage2-report.docx from stage2-report.md.
#
# Requires: pandoc, wkhtmltopdf.  (Ubuntu: sudo apt install pandoc wkhtmltopdf)
#
# The intermediate HTML is written into this folder so the relative image paths
# (diagrams/*.png) resolve, then removed again.
#
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v pandoc >/dev/null 2>&1; then
    echo "ERROR: pandoc is not installed."
    echo "  Ubuntu/Debian: sudo apt install pandoc"
    echo "  macOS:         brew install pandoc"
    exit 1
fi

echo "Regenerating diagrams..."
if command -v python3 >/dev/null 2>&1; then
    (cd diagrams && python3 generate_diagrams.py >/dev/null)
fi
if command -v dot >/dev/null 2>&1; then
    (cd diagrams && dot -Tpng -Gdpi=100 class-diagram.dot -o class-diagram.png 2>/dev/null || true)
    (cd diagrams && dot -Tsvg class-diagram.dot -o class-diagram.svg 2>/dev/null || true)
fi
if command -v rsvg-convert >/dev/null 2>&1; then
    for name in use-case-diagram sd01-perform-code-analysis sd02-manage-review-finding \
                sd03-generate-export-report sd04-autonomous-audit; do
        [ -f "diagrams/$name.svg" ] && \
            rsvg-convert -z 1.6 -b white "diagrams/$name.svg" -o "diagrams/$name.png"
    done
fi

echo "Building HTML..."
pandoc stage2-report.md \
    -f markdown \
    -t html5 \
    --standalone \
    --css report.css \
    --metadata pagetitle="Stage 2 Implementation Report - AI Code Review Agent" \
    -o _report.html

echo "Building PDF..."
if command -v wkhtmltopdf >/dev/null 2>&1; then
    # Note: this build intentionally avoids --footer-*, --quiet and
    # --enable-smart-shrinking, because distro builds of wkhtmltopdf are compiled
    # against an unpatched Qt and reject those options.
    wkhtmltopdf \
        --enable-local-file-access \
        --page-size A4 \
        --margin-top 16mm --margin-bottom 16mm \
        --margin-left 14mm --margin-right 14mm \
        --image-quality 94 \
        _report.html ../stage2-report.pdf
else
    echo "wkhtmltopdf not found; falling back to pandoc's PDF writer."
    pandoc stage2-report.md -o ../stage2-report.pdf
fi

echo "Building DOCX..."
pandoc stage2-report.md \
    -f markdown \
    -t docx \
    --toc --toc-depth=2 \
    -o ../stage2-report.docx

rm -f _report.html

echo
echo "Done:"
ls -la ../stage2-report.pdf ../stage2-report.docx
