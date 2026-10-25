---
description: Independent review of a feature task list against the spec and plan (coverage, order, size, risks); saves a report
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
allowed-tools: Read, Grep, Glob, Write, Bash(git status:*), Bash(git branch:*), Bash(git log:*), Bash(ls:*)
---

You are an independent reviewer of a task list. Find problems; do not fix them. Do not edit any file except the report named below.

Feature folder: `$ARGUMENTS`. If empty, use `specs/<current git branch>`; if that does not exist, use the newest folder in `specs/`.

Read: `tasks.md`, `spec.md`, `plan.md`, `data-model.md`, `quickstart.md` and `contracts/` in the feature folder; `.specify/memory/constitution.md`; `CLAUDE.md`.

Check each of these and cite task IDs for every finding:
1. **Coverage**: build a table of FR to implementation task(s) to test task(s). List every FR with no implementation task or no test task, and every success criterion with no way of being verified.
2. **Order and dependencies**: tests come before the code they test, migrations before entities, shared code before stories. Check every [P] marker: parallel tasks must not touch the same file or depend on each other.
3. **Size**: tasks too big for one reviewable commit (many files or more than one concern). Suggest where to split.
4. **Specificity**: each task names exact files and the behaviour expected; flag vague tasks.
5. **Missing tasks**: configuration, seed data, error handling, documentation, CI, security review, manual checks.
6. **Wrong or risky assumptions** inside task text: timing in tests, transaction behaviour, database constraints, framework behaviour. Say how sure you are, and say what to verify.
7. **Constitution**: tasks that would break a principle (money types, applied migrations, audit in the same transaction, deleting people, real data in fixtures).
8. **Dependencies on the owner**: mockups, decisions, files, installs that must exist before a task starts.
9. **First batch**: recommend which tasks to give to `/speckit-implement` first (about 5 to 8), and which tasks should be done in plan mode.

Write the report to `docs/reports/<feature folder name>/review-tasks.md` with this layout:
- Verdict: READY, READY WITH FIXES, or NOT READY
- Coverage table
- Blocking
- Should fix
- Minor
- Recommended first batch
Number every finding (B1, S1, M1) and give: task IDs, the problem, and a one-line suggested fix. If you are unsure about something, say so; never guess.

Then reply in chat with the verdict, the counts, the five most important findings and the report path. Do not paste the whole report.
