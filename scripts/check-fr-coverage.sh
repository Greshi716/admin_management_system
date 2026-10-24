#!/usr/bin/env bash
# Lists FR ids from the F0 spec that no test references. Report-only unless --strict is given.
set -euo pipefail
cd "$(dirname "$0")/.."

strict=0
[ "${1:-}" = "--strict" ] && strict=1

spec=specs/001-foundation/spec.md
frs=$(grep -oE '^- \*\*FR-[0-9]{3}' "$spec" | grep -oE 'FR-[0-9]{3}' | sort -u)

uncovered=()
total=0
for fr in $frs; do
  total=$((total + 1))
  if [ -d management/src/test ] && grep -rqE "@Covers\(\"$fr\"\)" management/src/test; then continue; fi
  found=0
  for dir in frontend/e2e frontend/src; do
    [ -d "$dir" ] || continue
    if grep -rE --include='*.spec.ts' "(it|test|describe)(\.[a-z]+)?\s*\(.*$fr" "$dir" >/dev/null 2>&1; then found=1; break; fi
  done
  [ "$found" -eq 1 ] || uncovered+=("$fr")
done

echo "FR coverage: $((total - ${#uncovered[@]})) of $total covered"
if [ "${#uncovered[@]}" -gt 0 ]; then
  echo "Uncovered: ${uncovered[*]}"
  if [ "$strict" -eq 1 ]; then exit 1; fi
fi
