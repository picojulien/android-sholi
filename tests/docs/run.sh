#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

JDK_BIN="$ROOT_DIR/env/java11/bin"
JAVAC="$JDK_BIN/javac"
JAVA="$JDK_BIN/java"

OUT_DIR="tests/docs/out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

"$JAVAC" -d "$OUT_DIR" tests/docs/src/*.java
"$JAVA" -cp "$OUT_DIR" docs.DocumentationQaTestRunner
