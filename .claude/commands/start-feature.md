---
description: Update main and create the next feature branch (use before /speckit-specify)
argument-hint: <short-name, for example fees>
allowed-tools: Bash(git status:*), Bash(git checkout:*), Bash(git pull:*), Bash(git branch:*), Bash(git fetch:*), Bash(ls:*)
---

Start a new feature called "$ARGUMENTS" by following `.claude/rules/git-workflow.md` exactly: check for uncommitted changes, update main with a fast-forward pull, work out the next feature number from `specs/`, and create the branch `NNN-$ARGUMENTS`. Report the branch name. Do not run `/speckit-specify` yet.
