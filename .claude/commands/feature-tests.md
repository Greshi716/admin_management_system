---
description: Plan, write and run the tests for a feature using three separate agents (planner, implementer, executor); saves reports
argument-hint: [feature folder, default = current branch, e.g. 001-foundation]
---

Feature folder: `$ARGUMENTS` (if empty, `specs/<current git branch>`). Reports go to `docs/reports/<feature folder name>/`.

Run these three agents one after another. Each must finish before the next starts, and each gets only the feature name and the paths it needs.

1. Use the `test-planner` agent. It plans from the spec only. If its report starts with `Result: BLOCKED`, stop and show me the questions.
2. Use the `test-implementer` agent. It writes tests from the plan. It must not change production code.
3. Use the `test-executor` agent. It runs everything and classifies failures.

Do not fix anything in this command. When done, reply with: the plan size, tests written vs planned, the execution result, and the five most important findings with the report path. Tell me the next step is `/fix-findings`.
