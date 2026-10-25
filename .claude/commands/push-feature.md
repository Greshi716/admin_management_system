---
description: Check that the feature is ready (tests passed, self-review approved, nothing uncommitted), push the branch to origin and create and push a version tag. Never merges and never opens a pull request.
argument-hint: [feature folder, default = current branch]
allowed-tools: Read, Grep, Glob, Bash(git status:*), Bash(git branch:*), Bash(git log:*), Bash(git diff:*), Bash(git push:*), Bash(git tag:*), Bash(git remote:*), Bash(bash scripts/verify.sh), Bash(bash scripts/check-fr-coverage.sh --strict), Bash(head:*), Bash(ls:*)
---

Feature folder: `$ARGUMENTS` (if empty, `specs/<current git branch>`). Check in this order and stop at the first failure, saying what to run:

1. The current branch is not `main`. Never push `main`.
2. `git status --short` is empty. If not, stop and list the files; do not commit or stash for me.
3. `docs/reports/<feature>/test-results.md` exists and its first line is `Result: PASS`, and `self-review.md` exists with first line `Result: APPROVED`. Also check that those reports are newer than the last code change: compare with `git log -1 --format=%cI` for source files; if the code changed after the reports, stop and ask for `/feature-tests` or `/self-review` to be re-run.
4. Verification is enforced by the project's push hook (`.claude/hooks/verify-before-push.sh`): it runs `scripts/verify.sh` and the strict FR coverage check before any `git push` and blocks the push if either fails. Do not run them a second time here. If the hook blocks the push, show me its output and stop.
5. Push: `git push -u origin <current branch>`. Never use `--force` or `--force-with-lease`. If the push is rejected, stop and report; do not rebase or merge on your own.

6. Tag: create an annotated tag named `<current branch>-vN` on the pushed commit, where N is the next free number (look at `git tag --list '<current branch>-v*'`; start at 1). The message is the feature title from `spec.md` plus the result lines of the test and self-review reports. Then `git push origin <tag>`. Never delete, move or overwrite an existing tag, and never use `--force`. If the same commit already has a tag of this pattern, do not create another; report the existing one.

Then tell me the branch name, the tag name and the commit count. Do not open a pull request; that needs my explicit go-ahead (task T104).
