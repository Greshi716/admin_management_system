#!/usr/bin/env bash
# Lists FR ids that no test references. Report-only unless --strict is given.
#  1. Every FR of the F0 spec (specs/001-foundation) must be referenced by some test.
#  2. On a feature branch NNN-name (not F0) every FR of specs/NNN-name/spec.md must be referenced by a test file that
#     the branch added or changed against main. Feature specs reuse the ids FR-001, FR-002, ... so the whole-tree
#     search of step 1 cannot tell the features apart; the diff against main can.
# A reference is @Covers("FR-xxx") in a Java test, or the id in the title of an it/test/describe in a *.spec.ts.
set -euo pipefail
cd "$(dirname "$0")/.."

strict=0
[ "${1:-}" = "--strict" ] && strict=1

# Test files to search; an empty list means all test files.
covered_in() { # $1 = FR id, remaining args = files (none = whole tree)
  local fr=$1; shift
  if [ "$#" -eq 0 ]; then
    if [ -d management/src/test ] && grep -rqE "@Covers\(\"$fr\"\)" management/src/test; then return 0; fi
    for dir in frontend/e2e frontend/src; do
      [ -d "$dir" ] || continue
      if grep -rE --include='*.spec.ts' "(it|test|describe)(\.[a-z]+)?\s*\(.*$fr" "$dir" >/dev/null 2>&1; then return 0; fi
    done
    return 1
  fi
  local f
  for f in "$@"; do
    case "$f" in
      *.java) grep -qE "@Covers\(\"$fr\"\)" "$f" && return 0 ;;
      *.spec.ts) grep -qE "(it|test|describe)(\.[a-z]+)?\s*\(.*$fr" "$f" && return 0 ;;
    esac
  done
  return 1
}

failed=0
check() { # $1 = label, $2 = spec file, remaining = files to search
  local label=$1 spec=$2; shift 2
  local frs total=0 uncovered=() fr
  frs=$(grep -oE '^- \*\*FR-[0-9]{3}' "$spec" | grep -oE 'FR-[0-9]{3}' | sort -u)
  for fr in $frs; do
    total=$((total + 1))
    covered_in "$fr" "$@" || uncovered+=("$fr")
  done
  echo "FR coverage ($label): $((total - ${#uncovered[@]})) of $total covered"
  if [ "${#uncovered[@]}" -gt 0 ]; then
    echo "Uncovered ($label): ${uncovered[*]}"
    failed=1
  fi
}

check "F0, specs/001-foundation" specs/001-foundation/spec.md

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)
if [ -n "$branch" ] && [ "$branch" != "main" ] && [ "$branch" != "001-foundation" ] && [ -f "specs/$branch/spec.md" ]; then
  if git rev-parse --verify -q main >/dev/null; then
    mapfile -t changed < <(git diff --name-only main...HEAD -- management/src/test frontend/e2e frontend/src | grep -E '\.(java|spec\.ts)$' | while read -r f; do [ -f "$f" ] && echo "$f"; done)
    [ "${#changed[@]}" -gt 0 ] || changed=(/nonexistent-no-test-file-changed)
    check "$branch, files changed on the branch" "specs/$branch/spec.md" "${changed[@]}"
  else
    echo "FR coverage ($branch): not checked, there is no local main branch to compare against"
    failed=1
  fi
fi

if [ "$failed" -eq 1 ] && [ "$strict" -eq 1 ]; then exit 1; fi
exit 0
