#!/usr/bin/env bash
#
# Creates your local .env file and puts your API key in it.
#
# Run it with:   ./setup-key.sh
#
# Why this exists: .env starts with a dot, so file managers hide it by default and it is
# easy to think the file was never created. This script creates it, writes your key into
# it, checks the key actually works, and confirms Git is ignoring it - without you having
# to find the file at all.
#
set -euo pipefail

cd "$(dirname "$0")"

echo
echo "=============================================="
echo "  AI Code Review Agent - API key setup"
echo "=============================================="
echo

# --------------------------------------------------------------- create the file

if [ ! -f .env.example ]; then
    echo "ERROR: .env.example is missing."
    echo "Are you running this from inside the project folder?"
    echo "You are currently in: $(pwd)"
    exit 1
fi

if [ -f .env ]; then
    echo "You already have a .env file."
    CURRENT=$(grep -E '^LLM_API_KEY=' .env | head -1 | cut -d= -f2- || true)
    if [ -n "$CURRENT" ] && [ "$CURRENT" != "YOUR_LOCAL_API_KEY_HERE" ]; then
        MASKED="****${CURRENT: -4}"
        echo "It currently holds the key: $MASKED"
    else
        echo "It does not have a real key in it yet."
    fi
    echo
    printf "Replace it? [y/N]: "
    read -r REPLY
    case "$REPLY" in
        [yY]*) ;;
        *) echo "Left unchanged. Nothing to do."; exit 0 ;;
    esac
    echo
else
    cp .env.example .env
    echo "Created your .env file from the template."
    echo
fi

# --------------------------------------------------------------- ask for the key

echo "Now paste your API key."
echo
echo "  Don't have one yet? Get a free one, no credit card needed:"
echo "    1. Go to  https://console.groq.com"
echo "    2. Sign up, then click 'API Keys' on the left"
echo "    3. Click 'Create API Key' and copy it"
echo
echo "  (The key starts with 'gsk_'. Nothing will appear as you paste -"
echo "   that is deliberate, so your key stays off the screen.)"
echo
printf "Paste your key and press Enter: "
read -rs API_KEY
echo
echo

if [ -z "$API_KEY" ]; then
    echo "Nothing was pasted. Run ./setup-key.sh again when you have your key."
    exit 1
fi

# Trim stray whitespace from the paste.
API_KEY="$(printf '%s' "$API_KEY" | tr -d '[:space:]')"

if [ "${API_KEY:0:4}" != "gsk_" ]; then
    echo "Note: that does not start with 'gsk_', so it may not be a Groq key."
    echo "Saving it anyway - if you are using another provider, remember to set"
    echo "LLM_PROVIDER in .env as well."
    echo
fi

# --------------------------------------------------------------- write it in

# Rewrite the LLM_API_KEY line in place, leaving every other setting and comment alone.
python3 - "$API_KEY" <<'PYTHON' 2>/dev/null || {
import io, sys
key = sys.argv[1]
with io.open('.env', encoding='utf-8') as handle:
    lines = handle.readlines()
written = False
for index, line in enumerate(lines):
    if line.strip().startswith('LLM_API_KEY='):
        lines[index] = 'LLM_API_KEY=' + key + '\n'
        written = True
        break
if not written:
    lines.append('\nLLM_API_KEY=' + key + '\n')
with io.open('.env', 'w', encoding='utf-8') as handle:
    handle.writelines(lines)
PYTHON
    # Fallback when python3 is unavailable.
    grep -v -E '^LLM_API_KEY=' .env > .env.tmp || true
    printf 'LLM_API_KEY=%s\n' "$API_KEY" >> .env.tmp
    mv .env.tmp .env
}

echo "Saved. Your .env now holds the key ****${API_KEY: -4}"
echo

# --------------------------------------------------------------- safety check

if command -v git >/dev/null 2>&1 && [ -d .git ]; then
    if git check-ignore -q .env 2>/dev/null; then
        echo "[OK] Git is ignoring .env, so your key can never be committed by accident."
    else
        echo "[!!] WARNING: Git is NOT ignoring .env. Do not commit until this is fixed."
        echo "     Check that .gitignore contains a line reading: .env"
    fi
    echo
fi

# --------------------------------------------------------------- does it work?

echo "Checking that the key works..."
echo

if [ ! -d target/classes ]; then
    echo "The application is not built yet, so let me build it first."
    ./build.sh >/dev/null 2>&1 || {
        echo "The build failed. Run ./build.sh on its own to see why."
        exit 1
    }
fi

OUTPUT=$(java -cp target/classes com.eecs3311.codereview.app.Main \
    --cli --repo samples/demo-project \
    --command "summarize PriceCalculator.java" 2>&1 || true)

# The checks below are ordered deliberately: a network problem must be reported as a
# network problem, not as a bad key, or someone behind a firewall will waste time
# regenerating a key that was fine all along. They match the application's own wording
# rather than raw HTTP codes, which is more reliable.

if printf '%s' "$OUTPUT" | grep -q "OFFLINE STRUCTURAL SUMMARY"; then
    echo "[!!] The key is not being picked up - the app is still in OFFLINE MODE."
    echo
    echo "     Things to check:"
    echo "       - Is .env in this folder? Run:  ls -a | grep env"
    echo "       - Does the line read  LLM_API_KEY=gsk_...  with no spaces around the = ?"
    echo "       - Run ./setup-key.sh again and re-paste the key."
    exit 1

elif printf '%s' "$OUTPUT" | grep -qi "Network error contacting"; then
    echo "[??] Could not reach the provider, so the key could not be checked."
    echo
    echo "     Your key has still been saved. This is a connection problem, not a key"
    echo "     problem - do NOT regenerate your key."
    echo
    echo "     Things to check:"
    echo "       - Are you online?"
    echo "       - Are you on a school or work network that blocks outside sites?"
    echo "         Try again on a home network or a phone hotspot."
    echo
    echo "     Once you are online, check the key with:"
    echo "       ./run.sh --cli --command \"summarize PriceCalculator.java\" --repo samples/demo-project"
    exit 1

elif printf '%s' "$OUTPUT" | grep -qi "API key was rejected"; then
    echo "[!!] The provider rejected the key."
    echo
    echo "     It may be mistyped, or it may have been deleted at the console."
    echo "     Make a new one at https://console.groq.com and run ./setup-key.sh again."
    exit 1

elif printf '%s' "$OUTPUT" | grep -qi "model name was not found"; then
    echo "[!!] Your key is fine, but the model it asked for no longer exists."
    echo
    echo "     Providers retire model IDs every few months. Trying the current"
    echo "     recommended one instead..."
    echo

    if grep -qE '^LLM_MODEL=' .env; then
        sed -i.bak 's|^LLM_MODEL=.*|LLM_MODEL=openai/gpt-oss-120b|' .env && rm -f .env.bak
    else
        printf '\nLLM_MODEL=openai/gpt-oss-120b\n' >> .env
    fi

    RETRY=$(java -cp target/classes com.eecs3311.codereview.app.Main \
        --cli --repo samples/demo-project \
        --command "summarize PriceCalculator.java" 2>&1 || true)

    if printf '%s' "$RETRY" | grep -qi "model name was not found"; then
        echo "     That one is gone too. Open https://console.groq.com/docs/models,"
        echo "     copy a current model ID, and put it in your .env file as:"
        echo "       LLM_MODEL=whatever-you-copied"
        exit 1
    fi

    echo "[OK] Fixed - now using openai/gpt-oss-120b."
    echo "     The model's reply to a real request:"
    echo
    printf '%s\n' "$RETRY" | grep -v "^Picked up" | tail -n 12 | sed 's/^/     /'

elif printf '%s' "$OUTPUT" | grep -qi "Rate limit reached"; then
    echo "[OK] The key works. (The provider is rate-limiting right now, which means"
    echo "     it recognised your key - just wait a minute before using the app.)"

else
    echo "[OK] The key works. Here is the model's reply to a real request:"
    echo
    printf '%s\n' "$OUTPUT" | grep -v "^Picked up" | tail -n 12 | sed 's/^/     /'
fi

echo
echo "=============================================="
echo "  Setup complete."
echo
echo "  Start the app with:"
echo "    ./run.sh            graphical interface"
echo "    ./run.sh --cli      command line"
echo "=============================================="
echo
