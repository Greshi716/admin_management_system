---
description: Run only the tests of one phase or user story (for example phase-2, us1, us2) and report real results, without changing any file
argument-hint: [phase-1 | phase-2 | us1 | us2 | us3 | us4 | us5 | us6 | all]
allowed-tools: Read, Grep, Glob, Bash(bash scripts/verify.sh), Bash(bash scripts/check-fr-coverage.sh), Bash(cd management && ./mvnw -B test:*), Bash(cd management && ./mvnw -B verify:*), Bash(cd frontend && npx playwright test:*), Bash(cd frontend && npm test:*)
---

Scope: `$ARGUMENTS` (if empty, ask which one). Do not edit any file. Do not fix failures; only report them.

Find the tests of the scope in `specs/<current branch>/tasks.md`: the "Tests for ..." section of that phase or user story lists the test files (the `BT/` backend test classes and the `frontend/e2e` or `.spec.ts` files). Phase 1 and 2 tests are the ones in those phases (for example `SchemaIT`, `SensitiveMaskerTest`, the table framework tests).

Run only those:
- Backend: from `management`, `./mvnw -B test -Dtest="Class1,Class2,..."` (use `verify` instead when the classes end in `IT` and the build needs it).
- Frontend: `cd frontend && npm test -- --watch=false` for unit tests, and `npx playwright test <spec files>` for end-to-end tests. Skip the frontend if `frontend/package.json` does not exist yet or Node is too old, and say so.
- For `all`, run `bash scripts/verify.sh`.

Then report: how many tests ran, passed, failed and were skipped; for each failure the test name, the assertion message and the likely file to look at; which FRs in the scope have no test yet (`bash scripts/check-fr-coverage.sh`). Never claim a test passed that did not run.
