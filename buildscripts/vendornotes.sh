#!/usr/bin/env bash
set -euo pipefail

# ── Config (override via env or TeamCity params) ────────────────────────────────
: "${GITHUB_NOTES_REPO:=Varshraghu98/release-notes}"   # owner/repo of notes source
: "${VENDOR_DIR:=vendor/release-notes}"                # parent repo path to place vendored files
: "${PR_BASE:=main}"                                   # parent repo branch to update
: "${REL_PATH:?REL_PATH must be set (e.g. docs/mysql-9.0-relnotes-en.pdf)}"
# NOTES_SHA preferred via env; else read .dep/update-notes/notes-sha.txt
# GIT_USER_NAME / GIT_USER_EMAIL optional; defaults below
# ────────────────────────────────────────────────────────────────────────────────

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: '$1' not found in PATH" >&2; exit 127; }; }
need git
# sha256: prefer sha256sum; fallback to macOS shasum
SHA256_TOOL="sha256sum"
command -v sha256sum >/dev/null 2>&1 || SHA256_TOOL="shasum -a 256"

NOTES_SOURCE_GIT_REPO="$GITHUB_NOTES_REPO"
VENDORED_NOTES_DIR="$VENDOR_DIR"
PARENT_REPO_TARGET_BRANCH="$PR_BASE"

# Resolve commit to vendor
if [ -n "${NOTES_SHA:-}" ]; then
  NOTES_SOURCE_COMMIT_SHA="$NOTES_SHA"
elif [ -f ".dep/update-notes/notes-sha.txt" ]; then
  NOTES_SOURCE_COMMIT_SHA="$(tr -d '[:space:]' < .dep/update-notes/notes-sha.txt)"
else
  echo "ERROR: NOTES_SHA not set and .dep/update-notes/notes-sha.txt not found" >&2
  exit 2
fi

echo "→ Vendoring from '$NOTES_SOURCE_GIT_REPO' @ $NOTES_SOURCE_COMMIT_SHA"
echo "→ Source path: $REL_PATH"
echo "→ Target dir : $VENDORED_NOTES_DIR"
echo "→ Branch     : $PARENT_REPO_TARGET_BRANCH"

# Ensure we are on the target branch
git fetch origin "$PARENT_REPO_TARGET_BRANCH"
git checkout "$PARENT_REPO_TARGET_BRANCH"
git pull --rebase origin "$PARENT_REPO_TARGET_BRANCH"

# Clone notes repo at the exact commit (shallow, temp dir)
TEMP_NOTES_CLONE_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TEMP_NOTES_CLONE_DIR"; }
trap cleanup EXIT

git clone --no-checkout "git@github.com:${NOTES_SOURCE_GIT_REPO}.git" "$TEMP_NOTES_CLONE_DIR/notes"
git -C "$TEMP_NOTES_CLONE_DIR/notes" fetch --depth=1 origin "$NOTES_SOURCE_COMMIT_SHA":"$NOTES_SOURCE_COMMIT_SHA"
git -C "$TEMP_NOTES_CLONE_DIR/notes" checkout --force "$NOTES_SOURCE_COMMIT_SHA"

# Validate REL_PATH exists at that commit
if ! git -C "$TEMP_NOTES_CLONE_DIR/notes" ls-tree -r --name-only "$NOTES_SOURCE_COMMIT_SHA" | grep -Fxq "$REL_PATH"; then
  echo "ERROR: '$REL_PATH' not found at commit $NOTES_SOURCE_COMMIT_SHA in $NOTES_SOURCE_GIT_REPO" >&2
  exit 1
fi

mkdir -p "$VENDORED_NOTES_DIR"
RELEASE_NOTES_FILENAME="$(basename "$REL_PATH")"
TARGET_FILE="$VENDORED_NOTES_DIR/$RELEASE_NOTES_FILENAME"

# Extract file
git -C "$TEMP_NOTES_CLONE_DIR/notes" show "$NOTES_SOURCE_COMMIT_SHA:$REL_PATH" > "$TARGET_FILE"
echo "✓ Copied $REL_PATH → $TARGET_FILE"

# Compute checksum
CHECKSUM="$($SHA256_TOOL "$TARGET_FILE" | awk '{print $1}')"
echo "✓ SHA-256: $CHECKSUM"

# Prepare manifest (atomic write)
UPSTREAM_MANIFEST_PATH="$TEMP_NOTES_CLONE_DIR/notes/manifest.txt"
LOCAL_MANIFEST_PATH="$VENDORED_NOTES_DIR/manifest.txt"
TMP_MANIFEST="$LOCAL_MANIFEST_PATH.tmp"

{
  # Start with upstream manifest if present
  if [ -f "$UPSTREAM_MANIFEST_PATH" ]; then
    cat "$UPSTREAM_MANIFEST_PATH"
  fi

  # Ensure we do not duplicate keys
  # (portable sed: create a backup then remove it)
  # We post-filter duplicates by re-emitting keys we control below.

} > "$TMP_MANIFEST"

# Remove lines we’ll re-define
# Use portable sed across GNU/BSD
sed -i.bak -E \
  -e '/^release_notes_repo_commit=/d' \
  -e '/^release_notes_source_path=/d' \
  -e '/^release_notes_filename=/d' \
  -e '/^release_notes_sha256=/d' \
  -e '/^release_notes_fetched_at=/d' \
  "$TMP_MANIFEST"
rm -f "$TMP_MANIFEST.bak"

# Append our provenance block
{
  echo "release_notes_repo_commit=$NOTES_SOURCE_COMMIT_SHA"
  echo "release_notes_source_path=$REL_PATH"
  echo "release_notes_filename=$RELEASE_NOTES_FILENAME"
  echo "release_notes_sha256=$CHECKSUM"
  # Use RFC3339 UTC
  date -u +'"release_notes_fetched_at=%Y-%m-%dT%H:%M:%SZ"' | tr -d '"'
} >> "$TMP_MANIFEST"

# Atomic move
mv "$TMP_MANIFEST" "$LOCAL_MANIFEST_PATH"
echo "✓ Wrote manifest: $LOCAL_MANIFEST_PATH"

# Stage and push only if changes exist
git add "$TARGET_FILE" "$LOCAL_MANIFEST_PATH" 2>/dev/null || true
if git diff --cached --quiet; then
  echo "No changes in vendor directory. Nothing to push."
  exit 0
fi

: "${GIT_USER_NAME:=TeamCity Bot}"
: "${GIT_USER_EMAIL:=tc-bot@example.invalid}"
git config --local user.name  "$GIT_USER_NAME"
git config --local user.email "$GIT_USER_EMAIL"

SHORT_NOTES_COMMIT="$(git -C "$TEMP_NOTES_CLONE_DIR/notes" rev-parse --short "$NOTES_SOURCE_COMMIT_SHA")"
git commit -m "docs(notes): vendor ${RELEASE_NOTES_FILENAME} @ ${SHORT_NOTES_COMMIT}"
git pull --rebase origin "$PARENT_REPO_TARGET_BRANCH"
git push origin HEAD:"$PARENT_REPO_TARGET_BRANCH"

echo "✓ Pushed vendored notes ($RELEASE_NOTES_FILENAME) to $PARENT_REPO_TARGET_BRANCH"