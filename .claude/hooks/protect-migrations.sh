#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit): applied Flyway migrations (already on main) are never edited.
. "$(dirname "$0")/_lib.sh"
FILE="$(json_field file_path)"
[ -z "$FILE" ] && exit 0
FILE="${FILE//\\//}"
case "$FILE" in
  */db/migration/V*.sql) ;;
  *) exit 0 ;;
esac
ROOT="$(git -C "$PROJECT" rev-parse --show-toplevel 2>/dev/null)" || exit 0
REL="${FILE#"$ROOT"/}"
for REF in main origin/main; do
  if git -C "$ROOT" cat-file -e "$REF:$REL" 2>/dev/null; then
    block "'$REL' is already on $REF, so it may be applied in real databases. Add a new migration (next version number) instead of editing it."
  fi
done
exit 0
