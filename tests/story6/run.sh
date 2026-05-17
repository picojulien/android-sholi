#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

JDK_BIN="$ROOT_DIR/env/java11/bin"
JAVAC="$JDK_BIN/javac"
JAVA="$JDK_BIN/java"

OUT_DIR="tests/story6/out"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

"$JAVAC" -d "$OUT_DIR" \
  sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/items/*.java \
  sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/document/*.java \
  sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/merge/*.java \
  tests/story6/src/*.java

"$JAVA" -cp "$OUT_DIR" story6.Story6TestRunner
