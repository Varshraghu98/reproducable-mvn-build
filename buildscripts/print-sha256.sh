#!/usr/bin/env sh
set -eu

# TeamCity checkout dir (passed from DSL); fallback to current dir
CHECKOUT_DIR="${CHECKOUT_DIR:-$(pwd)}"

# Default output dir is the Maven target/ under the checkout
OUTPUT_DIR="${OUTPUT_DIR:-$CHECKOUT_DIR/target}"

# ZIP filename pattern for your assignment (wildcards OK)
DOCS_PATTERN="${DOCS_PATTERN:-repro-docs-*.zip}"

# Require sha256sum
if ! command -v sha256sum >/dev/null 2>&1; then
  echo "Error: 'sha256sum' not found. Install coreutils on the agent." >&2
  exit 127
fi


echo "Searching for ZIPs in: $OUTPUT_DIR (pattern: $DOCS_PATTERN)"


matches=$(find "$OUTPUT_DIR" -maxdepth 1 -type f -name "$DOCS_PATTERN" -print)

if [ -z "$matches" ]; then
  echo "No reproducible docs ZIPs found under $OUTPUT_DIR matching $DOCS_PATTERN"
fi

# Hash all matches
echo "$matches" | while IFS= read -r f; do
  [ -f "$f" ] || continue
  sum=$(sha256sum "$f" | awk '{print $1}')
  echo "SHA256($f) = $sum"
  printf "%s  %s\n" "$sum" "$(basename "$f")" > "$f.sha256"
done