#!/usr/bin/env sh
set -euo pipefail
IFS=$'\n\t'

# Directory where Maven places the reproducible docs package
OUTPUT_DIR="${OUTPUT_DIR:-target}"

# File name pattern for the reproducible docs ZIP
DOCS_PATTERN="${DOCS_PATTERN:-repro-docs-*.zip}"

# Find matching files
files=$(ls -1 "${OUTPUT_DIR}/${DOCS_PATTERN}" 2>/dev/null || true)

if [ -z "$files" ]; then
  echo "No reproducible docs ZIPs found under ${OUTPUT_DIR}/ matching ${DOCS_PATTERN}"
  exit 1
fi

for f in $files; do
  if command -v sha256sum >/dev/null 2>&1; then
    sum=$(sha256sum "$f" | cut -d ' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    sum=$(shasum -a 256 "$f" | cut -d ' ' -f1)
  elif command -v openssl >/dev/null 2>&1; then
    sum=$(openssl dgst -sha256 -r "$f" | cut -d ' ' -f1)
  else
    echo "No SHA-256 tool found (sha256sum/shasum/openssl)"
    exit 1
  fi

  echo "SHA256($f) = $sum"
  printf "%s  %s\n" "$sum" "$(basename "$f")" > "$f.sha256"
done
