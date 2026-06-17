#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f env/setup.sh ]]; then
  source env/setup.sh
fi

for script in \
  tests/docs/run.sh \
  tests/story1/run.sh \
  tests/story2/run.sh \
  tests/story3/run.sh \
  tests/story4/run.sh \
  tests/story5/run.sh \
  tests/story6/run.sh \
  tests/story7/run.sh \
  tests/story8/run.sh
do
  "$script"
done
