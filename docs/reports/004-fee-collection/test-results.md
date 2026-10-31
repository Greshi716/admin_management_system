Result: PASS

# Test results: 004-fee-collection (re-run after fix T1)

Date: 2026-10-30. Branch: 004-fee-collection. Fix under test: `Money.problem` refuses exponent-notation amounts (see fixes.md).

## Commands and outcome
- `bash scripts/verify.sh` ran to the end and printed `VERIFY OK` (exit 0).
- `bash scripts/check-fr-coverage.sh --strict`: `FR coverage: 46 of 46 covered`, `FR coverage on branch 004-fee-collection: 25 of 25 covered`.

## Counts
| Suite | Run | Passed | Failed | Skipped |
|---|---|---|---|---|
| Backend (Maven verify, Surefire) | 691 | 691 | 0 | 0 |
| Frontend unit (Vitest, 34 files) | 274 | 274 | 0 | 0 |
| Playwright (chromium) | 173 | 173 | 0 | 0 |

Backend build: BUILD SUCCESS. Frontend build: succeeded.

## Findings
- T1 (closed): the earlier FAIL on exponent-notation amounts (FR-023) is gone. No test failed in this run, so there is nothing to classify as PRODUCT BUG, TEST BUG, ENVIRONMENT or SPEC GAP.
- T2: uncovered FRs: none (46 of 46 overall, 25 of 25 for this branch).
- T3: skipped or disabled tests: none. Surefire, Vitest and Playwright report 0 skipped. A grep for `@Disabled`, `.skip(`, `test.fixme`, `xit(`, `xdescribe` and `fdescribe` found only an unrelated `deleteOnExit` call.
- T4: the log shows a Hibernate WARN for a foreign-key violation on `academic_years`. It comes from the e2e test "FR-020: a year used by a division cannot be deleted and says why", which passed. It is expected output, not a failure.
- T5: manual scenarios in `specs/004-fee-collection/quickstart.md` that still need a human run. I did not execute any manual scenario, and I did not map each of the 22 scenarios to an automated test:
  - Scenario 15: two browser windows and the "balance has changed" pop-up.
  - Scenario 16: printing on paper or in print preview (A4 layout, identical reprints).
  - Scenario 19: UPDATE and DELETE through the database (the "append-only" refusal).
  - Scenario 21: `void_receipt` run by a developer, per the runbook.
  - Scenario 22: reading `docs/runbooks/correct-receipt.md`.
  - Section 5, performance: 10,000 students and 10,000 receipts, with timings recorded in `quickstart-results.md`. That file exists, but I did not check its contents.
  - Scenarios 1 to 14, 17, 18 and 20 should be spot-checked in a browser, because I did not confirm how much of each the automated tests cover.

## Environment
JDK 21, Maven, Node, PostgreSQL (Docker) and port 8080 all worked. Nothing was missing.
