#!/usr/bin/env bash
#
# Starts the AI Code Review Agent.
#
#   ./run.sh                                  graphical interface
#   ./run.sh --cli                            command-line interface
#   ./run.sh --cli --repo samples/demo-project
#
# Builds first if the application has not been compiled yet.
#
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d target/classes ] || [ -z "$(ls -A target/classes 2>/dev/null)" ]; then
    echo "Not built yet - building first."
    ./build.sh
    echo
fi

if [ ! -f .env ]; then
    echo "NOTE: no .env file found, so the application will start in OFFLINE MODE."
    echo "      Copy .env.example to .env and add your API key for the full system."
    echo
fi

exec java -cp target/classes com.eecs3311.codereview.app.Main "$@"
