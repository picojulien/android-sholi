#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

JDK_BIN="$ROOT_DIR/env/java11/bin"
JAVAC="$JDK_BIN/javac"
JAVA="$JDK_BIN/java"

OUT_DIR="tests/story7/out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

"$JAVAC" -d "$OUT_DIR" \
  sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/credentials/*.java \
  tests/story7/src/*.java

"$JAVA" -cp "$OUT_DIR" story7.Story7TestRunner
