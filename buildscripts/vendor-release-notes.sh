#!/usr/bin/env bash
set -eu

git config --show-origin --get-regexp '^user\.'
git config --local --unset-all user.name  || true
git config --local --unset-all user.email || true

# Config (override via env or TeamCity params if needed)
: "${GITHUB_NOTES_REPO:=Varshraghu98/release-notes}"   # owner/repo of notes source
: "${VENDOR_DIR:=vendor/release-notes}"                # parent repo path to place vendored files
: "${PR_BASE:=main}"                                   # parent repo branch to update
: "${REL_PATH:?REL_PATH must be set (e.g. docs/mysql-9.0-relnotes-en.pdf)}"


NOTES_SOURCE_GIT_REPO="$GITHUB_NOTES_REPO"
VENDORED_NOTES_DIR="$VENDOR_DIR"
PARENT_REPO_TARGET_BRANCH="$PR_BASE"

# Resolve which commit of release-notes repo to vendor
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

# Ensuring local repo is reset to target branch tip
git fetch origin "$PARENT_REPO_TARGET_BRANCH"
git checkout "$PARENT_REPO_TARGET_BRANCH"
git reset --hard "origin/$PARENT_REPO_TARGET_BRANCH"

# Clone notes repo at the exact commit (shallow, temp dir)
TEMP_NOTES_CLONE_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TEMP_NOTES_CLONE_DIR"; }
trap cleanup EXIT

# Cloning notes repo into temp dir at the exact commit
#The commit hash is the artifact provided by the update-release notes job.
git clone --no-checkout "git@github.com:${NOTES_SOURCE_GIT_REPO}.git" "$TEMP_NOTES_CLONE_DIR/notes"
git -C "$TEMP_NOTES_CLONE_DIR/notes" fetch --depth=1 origin "$NOTES_SOURCE_COMMIT_SHA":"$NOTES_SOURCE_COMMIT_SHA"
git -C "$TEMP_NOTES_CLONE_DIR/notes" checkout --force "$NOTES_SOURCE_COMMIT_SHA"

# Verifying the requested file exists at that commit
if ! git -C "$TEMP_NOTES_CLONE_DIR/notes" ls-tree -r --name-only "$NOTES_SOURCE_COMMIT_SHA" | grep -Fxq "$REL_PATH"; then
  echo "ERROR: '$REL_PATH' not found at commit $NOTES_SOURCE_COMMIT_SHA in $NOTES_SOURCE_GIT_REPO" >&2
  exit 1
fi

# Copying release notes file into vendor dir
mkdir -p "$VENDORED_NOTES_DIR"
RELEASE_NOTES_FILENAME="$(basename "$REL_PATH")"
TARGET_FILE="$VENDORED_NOTES_DIR/$RELEASE_NOTES_FILENAME"
git -C "$TEMP_NOTES_CLONE_DIR/notes" show "$NOTES_SOURCE_COMMIT_SHA:$REL_PATH" > "$TARGET_FILE"
echo "✓ Copied $REL_PATH → $TARGET_FILE"

# Compute checksum (for reproducible builds verification)
CHECKSUM="$($SHA256_TOOL "$TARGET_FILE" | awk '{print $1}')"
echo "✓ SHA-256: $CHECKSUM"

# Update manifest (recording vendored commit for reproducibility tracking)
UPSTREAM_MANIFEST_PATH="$TEMP_NOTES_CLONE_DIR/notes/manifest.txt"
LOCAL_MANIFEST_PATH="$VENDORED_NOTES_DIR/manifest.txt"
TMP_MANIFEST="$LOCAL_MANIFEST_PATH.tmp"

{
  if [ -f "$UPSTREAM_MANIFEST_PATH" ]; then
    cat "$UPSTREAM_MANIFEST_PATH"
  fi
} > "$TMP_MANIFEST"


sed -i.bak -E \
  -e '/^release_notes_repo_commit=/d' \
  "$TMP_MANIFEST"
rm -f "$TMP_MANIFEST.bak"

{
  echo "release_notes_repo_commit=$NOTES_SOURCE_COMMIT_SHA"
} >> "$TMP_MANIFEST"
mv "$TMP_MANIFEST" "$LOCAL_MANIFEST_PATH"
echo "✓ Wrote manifest: $LOCAL_MANIFEST_PATH"

# Stage and commit only if there are actual changes
git add "$TARGET_FILE" "$LOCAL_MANIFEST_PATH" 2>/dev/null || true
if git diff --cached --quiet; then
  echo "No changes in vendor directory. Nothing to push."
  exit 0
fi

# Commit and push changes with bot identity
: "${GIT_USER_NAME:=TeamCity Bot}"
: "${GIT_USER_EMAIL:=tc-bot@example.invalid}"
git config --local user.name  "$GIT_USER_NAME"
git config --local user.email "$GIT_USER_EMAIL"

SHORT_NOTES_COMMIT="$(git -C "$TEMP_NOTES_CLONE_DIR/notes" rev-parse --short "$NOTES_SOURCE_COMMIT_SHA")"
git commit -m "docs(notes): vendor ${RELEASE_NOTES_FILENAME} @ ${SHORT_NOTES_COMMIT}"
git push origin HEAD:"$PARENT_REPO_TARGET_BRANCH"

echo "✓ Pushed vendored notes ($RELEASE_NOTES_FILENAME) to $PARENT_REPO_TARGET_BRANCH"