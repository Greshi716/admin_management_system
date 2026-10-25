---
description: Fix the findings from the test results (and the self-review if it exists), one at a time, then re-run the tests
argument-hint: [feature folder, default = current branch]
---

Feature folder: `$ARGUMENTS` (if empty, `specs/<current git branch>`). Read the newest `review-diff-*.md`, `test-results.md` and `self-review.md` in `docs/reports/<feature folder name>/` (whichever exist, and only findings that are still open).

Work through the findings in order, most severe first:
1. For each PRODUCT BUG or BLOCKING finding: fix the production code the smallest correct way. For a TEST BUG: fix the test. For ENVIRONMENT: fix the setup or tell me what I must do. For a SPEC GAP: do not decide; list it and ask me.
2. Never weaken, skip or delete a test just to make it pass. Never invent a business rule. Use plan mode for money, schema or auth changes.
3. After each fix, run the affected tests, then commit with the finding id and the FR id in the message (for example `fix T3 (FR-011): ...`).
4. When all are done, run `bash scripts/verify.sh` and write `docs/reports/<feature folder name>/fixes.md`: each finding id, what changed, the commit, or why it was not fixed.

Finish with: fixed, not fixed (with reasons), and the next step `/self-review`.
