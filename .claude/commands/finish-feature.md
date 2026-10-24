---
description: Finish a feature end to end: diff review, fix, tests, fix, self-review, fix, then push the branch and tag. Stops on anything that needs your decision.
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
---

Feature folder: `$ARGUMENTS` (if empty, `specs/<current git branch>`). Reports go to `docs/reports/<feature folder name>/`.

Before starting: the branch is not `main`; every task in `tasks.md` is ticked except tasks that need my explicit go-ahead (T104 opens a pull request and is never run here) and manual tasks I still owe (list them). If something else is unticked, stop and list it. Uncommitted changes: stop and list them; do not commit them for me.

Run the stages in order. A "fix" means carrying out the procedure in `.claude/commands/fix-findings.md` for the open findings of that stage (each fix its own commit; never weaken or delete tests; never invent a business rule; plan mode for money, schema or auth).

1. **Diff review.** Use the `diff-reviewer` agent on the whole branch. If its result is `FIX FIRST` or `DO NOT COMMIT`: fix, then run the agent again. 
2. **Tests.** Carry out `.claude/commands/feature-tests.md` (test-planner, test-implementer, test-executor). If the executor's result is `FAIL`: fix, then run the `test-executor` agent again.
3. **Self-review.** Use the `self-reviewer` agent. If `CHANGES REQUIRED`: fix, run the `test-executor` agent again (it must be `PASS`), then run the `self-reviewer` again.
4. **Push.** Carry out `.claude/commands/push-feature.md` (branch push and tag).

Rules:
- Repeat a fix-and-recheck loop at most 3 times per stage. If it still fails, stop and report what is left.
- Stop and ask me, instead of deciding, on: a SPEC GAP, a NEEDS-CLARIFICATION, a business rule not in the agreement, a failure caused by my machine (Node, database, JDK), anything touching money rules you are unsure about, or anything that would need a force-push or an edit of an applied migration.
- If a later fix changes code after an earlier report, the earlier gate that covers that code must be re-run before pushing (push-feature checks this).
- Do not open a pull request.

Finish with a short summary: for each stage its result and the number of fix rounds, the commits added, the branch and tag pushed, and anything left for me.
