---
description: Run the self-review agent over the whole feature branch after the findings are fixed; saves a report
argument-hint: [feature folder, default = current branch]
---

Feature folder: `$ARGUMENTS` (if empty, `specs/<current git branch>`).

Use the `self-reviewer` agent on this feature. When it finishes, show me its result line, the counts and the five most important findings with the report path.

If the result is `CHANGES REQUIRED`, the next step is `/fix-findings` and then `/self-review` again. If `APPROVED`, the next step is `/push-feature`.
