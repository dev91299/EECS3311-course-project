#!/usr/bin/env bash
#
# Builds the AI Code Review Agent without Maven.
#
# The project has no runtime dependencies, so a plain JDK is enough. Use this when Maven
# is unavailable or cannot reach the internet. Use ./run.sh afterwards to start the app.
#
#   ./build.sh          compile the application
#   ./build.sh --check  compile, then run the no-dependency verification harness
#
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac was not found."
    echo "Install a Java Development Kit (version 17 or newer), then try again."
    echo "  macOS:   brew install openjdk@21"
    echo "  Ubuntu:  sudo apt install openjdk-21-jdk"
    echo "  Windows: download from https://adoptium.net"
    exit 1
fi

VERSION=$(javac -version 2>&1 | grep -oE '[0-9]+' | head -1)
if [ "$VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or newer is required. Found version $VERSION."
    exit 1
fi

echo "Compiling..."
rm -rf target/classes
mkdir -p target/classes
# shellcheck disable=SC2046
javac -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')
echo "Build succeeded. Classes are in target/classes"

if [ "${1:-}" = "--check" ]; then
    echo
    echo "Running the verification harness..."
    mkdir -p target/tools
    javac -encoding UTF-8 -cp target/classes -d target/tools tools/SelfCheck.java
    java -cp target/classes:target/tools SelfCheck samples/demo-project
fi

echo
echo "Next steps:"
echo "  ./run.sh            start the graphical interface"
echo "  ./run.sh --cli      start the command-line interface"
