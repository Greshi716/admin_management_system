---
description: Independent review of the code changes for a task (or the whole branch) against the spec, tasks and constitution; saves a report (runs the diff-reviewer agent)
argument-hint: [task id or range, e.g. T014 or T030-T037; empty = everything on this branch]
---

Use the `diff-reviewer` agent. Scope: `$ARGUMENTS` (empty = everything on this branch). Pass it the scope and the current branch name.

When it finishes, reply with its result line, the counts, the five most important findings and the report path. Do not paste the whole report.
