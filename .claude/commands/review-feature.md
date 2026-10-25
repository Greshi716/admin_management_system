---
description: Final review of a finished feature before the pull request, tests, FR coverage, whole-branch diff, docs, go or no-go; saves a report
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
allowed-tools: Read, Grep, Glob, Write, Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git branch:*), Bash(git show:*), Bash(git ls-files:*), Bash(date:*), Bash(ls:*), Bash(bash scripts/verify.sh), Bash(bash scripts/check-fr-coverage.sh)
---

You are the final reviewer before a pull request. Find problems; do not fix them. Do not edit any file except the report named below.

Feature folder: `$ARGUMENTS`. If empty, use `specs/<current git branch>`.

Read: `spec.md`, `plan.md`, `tasks.md`, `quickstart.md` in the feature folder; `docs/agreement` (latest); `docs/decision-log.md`; `.specify/memory/constitution.md`; `CLAUDE.md`; earlier reports in `docs/reports/<feature folder name>/`; the whole-branch diff (`git diff main...HEAD`).

Do and report each of these:
1. **Tasks**: list every task not ticked, with the reason if the tasks file or commits explain it.
2. **Build and tests**: run `bash scripts/verify.sh` and `bash scripts/check-fr-coverage.sh`. Report the real results (counts, failures). If something cannot run here, say so.
3. **FR matrix**: every FR, the test file(s) that cover it, and whether that test passed in this run.
4. **Success criteria**: for each SC, how it was verified (automated test, performance test, manual) or that it was not.
5. **Quickstart scenarios**: for each, whether a report shows it was run; otherwise mark "needs manual run".
6. **Whole-branch diff**: constitution and never-do list (money types, edited applied migrations, deletes, audit in the same transaction, real data, secrets in code or logs, committed credentials or `.env`), tests weakened or skipped, security, module boundaries, leftover TODOs and debug code.
7. **Docs**: `CLAUDE.md` commands, quickstart, decision log, agreement and constitution version are up to date with what was built.
8. **Design**: compare the built screens with `docs/design/` (images and `ui-tokens.md`) as far as can be done by reading the code; say what needs a human look.
9. **Open items**: anything in the decision log or tasks that is still open and affects the next feature.

Write the report to `docs/reports/<feature folder name>/feature-report.md` with this layout:
- Merge decision: GO, GO WITH CONDITIONS, or NO-GO
- Blocking
- Conditions
- FR matrix
- Success criteria status
- Quickstart status
- Draft pull request title and description
Number every finding (B1, C1) and give: location, the problem, and a one-line suggested fix. If you are unsure about something, say so; never guess.

Then reply in chat with the decision, the counts, the five most important findings and the report path. Do not paste the whole report.
