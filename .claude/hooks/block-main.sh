#!/usr/bin/env bash
# PreToolUse (Bash): never commit or push on main/master.
. "$(dirname "$0")/_lib.sh"
CMD="$(json_field command)"
[ -z "$CMD" ] && exit 0
if is_git "$CMD" commit || is_git "$CMD" push; then
  B="$(current_branch)"
  if [ "$B" = "main" ] || [ "$B" = "master" ]; then
    block "you are on '$B'. Create a feature branch first (/start-feature <short-name>). Never commit or push on main."
  fi
  if is_git "$CMD" push && printf '%s' "$CMD" | grep -Eq "[[:space:]:](main|master)([[:space:]]|$)"; then
    block "pushing to main/master is not allowed. Push the feature branch and open a pull request."
  fi
fi
exit 0
