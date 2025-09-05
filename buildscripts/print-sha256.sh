#!/usr/bin/env sh
set -eu
IFS=$(printf '\n\t')

# Directory where Maven outputs your reproducible docs ZIP
OUTPUT_DIR="${OUTPUT_DIR:-target}"

# ZIP file name pattern for your assignment
DOCS_PATTERN="${DOCS_PATTERN:-repro-docs-*.zip}"

# Ensure sha256sum exists
if ! command -v sha256sum >/dev/null 2>&1; then
  echo "Error: 'sha256sum' not found. Install coreutils on the agent." >&2
  exit 127
fi

# Collect matching files
files=$(ls -1 "${OUTPUT_DIR}/${DOCS_PATTERN}" 2>/dev/null || true)
if [ -z "$files" ]; then
  echo "No reproducible docs ZIPs found under ${OUTPUT_DIR}/ matching ${DOCS_PATTERN}"
  exit 1
fi

for f in $files; do
  # sha256sum prints: "<hash>  <path>"
  line=$(sha256sum "$f")
  sum=${line%% *}   # take text before first space
  echo "SHA256($f) = $sum"
  printf "%s  %s\n" "$sum" "$(basename "$f")" > "$f.sha256"
done