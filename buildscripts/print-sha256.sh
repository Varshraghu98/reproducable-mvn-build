#!/usr/bin/env sh
set -eu

# Directory where Maven outputs the reproducible docs ZIP
OUTPUT_DIR="${OUTPUT_DIR:-target}"

# ZIP filename pattern for your assignment
DOCS_PATTERN="${DOCS_PATTERN:-repro-docs-*.zip}"

# Require sha256sum
if ! command -v sha256sum >/dev/null 2>&1; then
  echo "Error: 'sha256sum' not found. Install coreutils on the agent." >&2
  exit 127
fi

# Build the file list using shell globbing (DO NOT quote DOCS_PATTERN here)
set -- "$OUTPUT_DIR"/$DOCS_PATTERN

# If the glob didn't match, $1 stays as the literal pattern
if [ "$1" = "$OUTPUT_DIR"/$DOCS_PATTERN ]; then
  echo "No reproducible docs ZIPs found under $OUTPUT_DIR/ matching $DOCS_PATTERN"
  exit 1
fi

# Hash all matches
for f in "$@"; do
  [ -f "$f" ] || continue
  sum=$(sha256sum "$f" | awk '{print $1}')
  echo "SHA256($f) = $sum"
  printf "%s  %s\n" "$sum" "$(basename "$f")" > "$f.sha256"
done
