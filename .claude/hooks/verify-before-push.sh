#!/usr/bin/env bash
# PreToolUse (Bash): before any `git push`, the full verification must pass.
# Skips the run when HEAD was already verified and nothing is uncommitted (so the tag push after the branch push is free).
. "$(dirname "$0")/_lib.sh"
CMD="$(json_field command)"
[ -z "$CMD" ] && exit 0
is_git "$CMD" push || exit 0
cd "$PROJECT" || exit 0
HEAD_SHA="$(git rev-parse HEAD 2>/dev/null)"
MARK="$PROJECT/.claude/.last-verified"
if [ -f "$MARK" ] && [ "$(cat "$MARK")" = "$HEAD_SHA" ] && [ -z "$(git status --porcelain)" ]; then
  exit 0
fi
[ -f scripts/verify.sh ] || exit 0
echo "Running scripts/verify.sh before the push (this can take a few minutes)..." >&2
if ! OUT="$(bash scripts/verify.sh 2>&1)"; then
  printf '%s\n' "$OUT" | tail -n 40 >&2
  block "verify failed, so the push was stopped. Fix the failures first. Check JAVA_HOME points to JDK 21 and port 8080 is free."
fi
if [ -f scripts/check-fr-coverage.sh ]; then
  if ! OUT="$(bash scripts/check-fr-coverage.sh --strict 2>&1)"; then
    printf '%s\n' "$OUT" | tail -n 30 >&2
    block "strict FR coverage failed: some requirement has no test."
  fi
fi
printf '%s' "$HEAD_SHA" > "$MARK"
exit 0
