Result: FAIL

# Test results: 004-fee-collection (F3)

Run date 2026-10-30, branch 004-fee-collection, PostgreSQL 15.6 on localhost:5432 (test DB admin_management_test).

## Counts

| Stage | Result |
|---|---|
| `bash scripts/verify.sh` | exit 1. Backend `mvnw verify` BUILD FAILURE (04:48 min). The script stopped there, so its later stages did not run. |
| Backend tests | 688 run, 687 passed, 1 failed, 0 errors, 0 skipped |
| Frontend build (run by hand, same command as the script) | passed |
| Frontend unit tests (run by hand) | 34 files, 274 tests, all passed |
| Playwright e2e (run by hand) | 173 passed, 0 failed, 0 skipped |
| `check-fr-coverage.sh --strict` | exit 0. 46 of 46 FRs covered overall; 25 of 25 on branch 004-fee-collection |
| Disabled or skipped tests (grep for @Disabled, @Ignore, assumeTrue) | none; Surefire reports Skipped: 0 |

The frontend and e2e stages were run separately because verify.sh stops at the backend failure. They are not part of the verify.sh exit code, and I did not check whether e2e shares state with the failed run.

## Findings

### T1. PRODUCT BUG: exponent text accepted as an amount (the known failure)
- Test: `ReceiptEdgeCasesIT.anAmountSentAsExponentTextIsRefusedAndNothingIsSaved`, line 197.
- Assertion: expected a 4xx response, got 201. `POST /api/receipts` with `"feePaid":"1e3"` saves 1000.00.
- Spec: FR-023 (specs/004-fee-collection/spec.md line 204) requires an amount to be an exact decimal with two places, and an amount with more than two places to be refused, not rounded. "1e3" is not a two-place decimal, so it must be refused. Plan row FR023-F (test-plan.md line 323) expects 422 with a field error and nothing saved.
- Classification: PRODUCT BUG. The test is correct against the spec. The spec itself is not silent or contradictory here, so it is not a SPEC GAP. One caveat: FR-023 does not name exponent notation. The refusal follows from "exact decimal with two places" and from the plan row.
- Suggested fix: reject non-plain-decimal text for money fields. Either bind `feePaid` and `registrationPaid` as strings validated against `^\d+(\.\d{1,2})?$` before the BigDecimal conversion, or use a custom Jackson deserializer that refuses exponent notation. This belongs in the receipt request DTO or its deserializer under `management/src/main/java/com/admin/management/fees/`. A shared Money deserializer would also cover other money inputs. I did not check whether other forms of the same hole exist, such as `registrationPaid`.
- Why it blocks: it is the only failing test, but the stated PASS rule requires zero failures.

### T2. Verify script stops at the first failure
verify.sh did not reach the frontend and e2e stages. I ran them by hand and they passed (see the counts above). After T1 is fixed, re-run `bash scripts/verify.sh` so one run covers everything. No file change is needed.

### T3. Uncovered FRs
None. The strict coverage check reports 46 of 46 covered, and 25 of 25 for this branch.

### T4. Skipped or disabled tests
None. Surefire reports 0 skipped, Vitest and Playwright report none, and a grep found no @Disabled, @Ignore or assumeTrue.

### T5. Manual scenarios in specs/004-fee-collection/quickstart.md
I did not execute any manual scenario, and I did not map each of the 22 scenarios to an automated test. I did not check whether `quickstart-results.md` exists. The following still need a human run, or a human check that the automated e2e covers them:
- Scenario 15: two browser windows and the "balance has changed" pop-up.
- Scenario 16: printing receipts on paper or print preview, including A4 layout and identical reprints.
- Scenario 19: UPDATE and DELETE through the database (the "append-only" refusal).
- Scenario 21: `void_receipt` run by a developer, per the runbook.
- Scenario 22: reading `docs/runbooks/correct-receipt.md`.
- Section 5, performance: 10,000 students and 10,000 receipts, with timings recorded in `quickstart-results.md`.
Scenarios 1 to 14, 17, 18 and 20 should be spot-checked in a browser, because I could not confirm how much of each e2e covers.

## Environment
JDK, Maven, Node, PostgreSQL (Docker) and port 8080 all worked. Nothing was missing.
