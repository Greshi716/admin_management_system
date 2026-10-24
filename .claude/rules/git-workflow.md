# Git workflow: starting a feature

Applies whenever a new feature is started (before running `/speckit-specify` or creating any spec folder).

1. Run `git status --short`. If there are uncommitted changes, stop and ask the user whether to commit them first or carry them onto the new branch. Never stash, discard or reset on your own.
2. Switch to the main branch and update it: `git checkout main`, then `git pull --ff-only origin main`. If the pull fails or cannot fast-forward, stop and report. Do not merge or rebase on your own.
3. Pick the next feature number from the existing folders in `specs/` (for example, if `001-foundation` exists, the next is `002`). The branch name is `NNN-short-name`, the same as the spec folder name.
4. Create the branch: `git checkout -b NNN-short-name`.
5. Tell the user the branch name, then continue with `/speckit-specify`.

Other rules:
- If the current branch already is the branch for this feature, do not create another one.
- Never commit directly to `main`. Never force-push.
- Commit after every task, with the requirement ID (FR-xxx) in the message.
