---
description: Independent review of the code changes for a task (or the whole branch) against the spec, tasks and constitution; saves a report
argument-hint: [task id or range, e.g. T014 or T030-T037; empty = everything on this branch]
allowed-tools: Read, Grep, Glob, Write, Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git show:*), Bash(git ls-files:*), Bash(date:*), Bash(ls:*), Bash(bash scripts/verify.sh)
---

You are an independent code reviewer. Find problems; do not fix them. Do not edit any file except the report named below.

Scope: `$ARGUMENTS`. If a task ID or range is given, read those tasks in `specs/<current branch>/tasks.md` and review only the changes for them. If empty, review everything on this branch: `git diff main...HEAD` plus uncommitted changes (`git status`, `git diff`, `git diff --staged`).

Read: the changes; the tasks and the spec for the feature (`specs/<current branch>/`); `.specify/memory/constitution.md`; `CLAUDE.md`; `docs/design/ui-tokens.md` when the changes touch the frontend.

Check each of these and cite file and line for every finding:
1. **Task fit**: the change does what the task says, nothing more (scope creep), nothing less.
2. **Spec fit**: behaviour matches the FR text; tests are tagged with the FR (`@Covers` or the FR id in the test title).
3. **Constitution and never-do list**:
   - money held in float or double, or rounding other than HALF_UP
   - any change to an existing Flyway migration that is already on `main` (blocking)
   - hard deletes or cascade deletes of people, or changes to finalised records
   - a financial or master-data write without an audit entry in the same transaction
   - real or realistic personal data in fixtures, docs or logs
   - secrets, passwords, hashes or recovery codes in code, logs, responses, audit entries or committed files (check `git ls-files` for `.env`, `credentials-first-run.txt`, `backups/`, `real-data/`)
   - force-push or history rewriting
4. **Tests**: they exist and check behaviour. Flag weakened or deleted assertions, `@Disabled` or skipped tests, tests that cannot fail, and missing failure cases.
5. **Security**: authentication on every `/api` path, input validation, SQL built by string concatenation, CSRF, error messages that leak details, sensitive fields masked.
6. **Code quality**: module boundaries (other modules used only through their public API package), duplicated code, dead or commented-out code, TODOs, long methods, transaction boundaries, null handling.
7. **Frontend** (if touched): theme tokens only (no hard-coded colours), controls at least 44px, loading, empty and error states, labels for inputs and icon buttons, strict TypeScript with no `any`.
8. **Build**: if `bash scripts/verify.sh` has not been run since the latest change, run it and include the result. If it cannot run here, say so.

Get today's date and time with `date +%F-%H%M`. Write the report to `docs/reports/<current branch>/review-diff-<task id or branch>-<date>.md` with this layout:
- Verdict: OK TO COMMIT, FIX FIRST, or DO NOT COMMIT
- Blocking
- Should fix
- Minor
- Verify result
Number every finding (B1, S1, M1) and give: location, the problem, and a one-line suggested fix. If you are unsure about something, say so; never guess.

Then reply in chat with the verdict, the counts, the five most important findings and the report path. Do not paste the whole report.
