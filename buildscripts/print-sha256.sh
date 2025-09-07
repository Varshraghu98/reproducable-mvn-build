#!/usr/bin/env sh
set -eu

# Default paths and file pattern (can be overridden via env)
CHECKOUT_DIR="${CHECKOUT_DIR:-$(pwd)}"
OUTPUT_DIR="${OUTPUT_DIR:-$CHECKOUT_DIR/target}"
DOCS_PATTERN="${DOCS_PATTERN:-repro-docs-*.zip}"

echo "Searching for ZIPs in: $OUTPUT_DIR (pattern: $DOCS_PATTERN)"
# Find all matching ZIP files
matches=$(find "$OUTPUT_DIR" -maxdepth 1 -type f -name "$DOCS_PATTERN" -print)

# Generate sha256 checksum for each found ZIP
if [ -z "$matches" ]; then
  echo "No reproducible docs ZIPs found under $OUTPUT_DIR matching $DOCS_PATTERN"
fi

echo "$matches" | while IFS= read -r f; do
  [ -f "$f" ] || continue
  sum=$(sha256sum "$f" | awk '{print $1}')
  echo "SHA256($f) = $sum"
  printf "%s  %s\n" "$sum" "$(basename "$f")" > "$f.sha256"
done