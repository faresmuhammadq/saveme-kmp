#!/usr/bin/env bash
# Reports what changed in the backend's error catalogue and API conventions since the
# backend commit the newest spec was written against, and where the client's ApiErrorCode
# disagrees with the catalogue.
#
# Baseline: the highest-numbered specs/*/spec.md carrying
#   **Backend contract**: `saveme-backend@<sha>`
# The comparison is baseline commit -> backend working tree, so uncommitted backend edits count.
#
# Usage: check-backend-contract.sh [--baseline <sha>]
# Env:   SAVEME_BACKEND_DIR (default: ../saveme-backend relative to the repo root)

set -euo pipefail
export LC_ALL=C

REPO_ROOT="$(git rev-parse --show-toplevel)"
BACKEND_DIR="${SAVEME_BACKEND_DIR:-$REPO_ROOT/../saveme-backend}"
CATALOG="docs/openapi/error-catalog.yaml"
WATCHED=("docs/api-conventions.md" "$CATALOG" "docs/openapi/components")
CLIENT_CODES="$REPO_ROOT/shared/src/commonMain/kotlin/com/fares/saveme/core/network/ApiErrorCode.kt"

baseline=""
baseline_spec=""
if [[ "${1:-}" == "--baseline" ]]; then
    baseline="${2:?--baseline needs a commit}"
    baseline_spec="command line"
fi

if ! git -C "$BACKEND_DIR" rev-parse --git-dir >/dev/null 2>&1; then
    echo "ERROR: backend repository not found at $BACKEND_DIR (set SAVEME_BACKEND_DIR)" >&2
    exit 1
fi

if [[ -z "$baseline" ]]; then
    for spec in $(ls -d "$REPO_ROOT"/specs/*/ 2>/dev/null | sort -r); do
        sha=$(grep -oE '\*\*Backend contract\*\*: `saveme-backend@[0-9a-f]{7,40}`' "$spec/spec.md" 2>/dev/null \
            | grep -oE '[0-9a-f]{7,40}`$' | tr -d '`' || true)
        if [[ -n "$sha" ]]; then
            baseline="$sha"
            baseline_spec="${spec#"$REPO_ROOT"/}spec.md"
            break
        fi
    done
fi

head_sha=$(git -C "$BACKEND_DIR" rev-parse --short HEAD)
dirty=$(git -C "$BACKEND_DIR" status --porcelain -- "${WATCHED[@]}")

echo "# Backend contract check"
echo
echo "Backend: $BACKEND_DIR @ $head_sha$([[ -n "$dirty" ]] && echo " (+ uncommitted changes)")"
if [[ -z "$baseline" ]]; then
    echo "Baseline: NONE — no spec records a backend contract commit; showing current state only."
elif ! git -C "$BACKEND_DIR" cat-file -e "$baseline^{commit}" 2>/dev/null; then
    echo "ERROR: baseline $baseline (from $baseline_spec) is not a commit in the backend repository" >&2
    exit 1
else
    echo "Baseline: $baseline (from $baseline_spec)"
fi
echo "Record in the new spec: **Backend contract**: \`saveme-backend@$head_sha\`"

# Prints "CODE status level retryable deprecated" per catalogue entry, from YAML on stdin.
catalog_entries() {
    awk '
        /^x-error-catalog:/ { in_cat = 1; next }
        in_cat && /^[^ #]/  { in_cat = 0 }
        !in_cat             { next }
        function flush() { if (code != "") print code, status, level, retry, dep }
        /^  [A-Z_]+:/ { flush(); code = $1; sub(/:$/, "", code); status = level = retry = "-"; dep = "no"; next }
        /^    status:/     { status = $2 }
        /^    level:/      { level = $0; sub(/^ *level: */, "", level); gsub(/[][ ]/, "", level) }
        /^    retryable:/  { retry = $2 }
        /^    deprecated: *true/ { dep = "yes" }
        END { flush() }
    ' | sort
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
catalog_entries < "$BACKEND_DIR/$CATALOG" > "$tmp/now"

echo
echo "## Watched files changed since baseline"
if [[ -z "$baseline" ]]; then
    echo "(no baseline)"
else
    changed=$(git -C "$BACKEND_DIR" diff --stat "$baseline" -- "${WATCHED[@]}")
    untracked=$(git -C "$BACKEND_DIR" ls-files --others --exclude-standard -- "${WATCHED[@]}")
    if [[ -z "$changed$untracked" ]]; then
        echo "None — error catalogue and API conventions are unchanged."
    else
        [[ -n "$changed" ]] && echo "$changed"
        [[ -n "$untracked" ]] && echo "$untracked" | sed 's/^/new file: /'
    fi
fi

if [[ -n "$baseline" ]]; then
    git -C "$BACKEND_DIR" show "$baseline:$CATALOG" 2>/dev/null | catalog_entries > "$tmp/base" || true

    echo
    echo "## Error catalogue changes (code status level retryable deprecated)"
    added=$(join -v2 <(cut -d' ' -f1 "$tmp/base") <(cut -d' ' -f1 "$tmp/now") || true)
    removed=$(join -v1 <(cut -d' ' -f1 "$tmp/base") <(cut -d' ' -f1 "$tmp/now") || true)
    modified=$(join "$tmp/base" "$tmp/now" | awk '$2$3$4$5 != $6$7$8$9 {
        print $1 ": " $2 " " $3 " " $4 " " $5 " -> " $6 " " $7 " " $8 " " $9 }')
    [[ -z "$added$removed$modified" ]] && echo "None."
    for c in $added;   do echo "ADDED    $(grep "^$c " "$tmp/now")"; done
    for c in $removed; do echo "REMOVED  $c (a released code must never be deleted — raise with the backend)"; done
    [[ -n "$modified" ]] && echo "$modified" | sed 's/^/CHANGED  /'

    echo
    echo "## API conventions diff"
    conv=$(git -C "$BACKEND_DIR" diff -U2 "$baseline" -- docs/api-conventions.md docs/openapi/components)
    if [[ -z "$conv" ]]; then echo "None."; else echo '```diff'; echo "$conv"; echo '```'; fi
fi

echo
echo "## Client ApiErrorCode vs catalogue"
if [[ ! -f "$CLIENT_CODES" ]]; then
    echo "(no $CLIENT_CODES yet)"
else
    issues=0
    while read -r code retry_class; do
        [[ "$code" == "UNKNOWN" ]] && continue  # client fallback, not a catalogue code
        entry=$(grep "^$code " "$tmp/now" || true)
        if [[ -z "$entry" ]]; then
            echo "NOT IN CATALOGUE  $code"; issues=1; continue
        fi
        read -r _ _ _ retry dep <<< "$entry"
        expected=$(case "$retry" in automatic) echo Automatic;; after-change) echo AfterChange;; never) echo Never;; *) echo "?";; esac)
        [[ "$expected" != "$retry_class" ]] && { echo "RETRY MISMATCH    $code client=$retry_class catalogue=$retry"; issues=1; }
        [[ "$dep" == "yes" ]] && { echo "DEPRECATED        $code (see replaced_by in the catalogue)"; issues=1; }
    done < <(grep -oE '^\s+[A-Z_]+\(RetryClass\.[A-Za-z]+\)' "$CLIENT_CODES" | sed -E 's/^\s+([A-Z_]+)\(RetryClass\.([A-Za-z]+)\)/\1 \2/')
    [[ $issues -eq 0 ]] && echo "Consistent: every client code exists in the catalogue with the same retry class."
fi
exit 0
