Result: OK TO COMMIT

# Diff review: 004-fee-collection, round 3 (whole branch `main...HEAD`, after fix round 2)

- Branch: `004-fee-collection`, HEAD `9b1c4f5`. The working tree was clean before and after the verify run.
- Scope: the whole branch, with the focus on fix commit `9b1c4f5` (17 files, +144 / -14) and the round-2 findings in `review-diff-004-fee-collection-2026-10-30-1421.md` (B1, M1 to M4).
- Earlier reports: `review-diff-004-fee-collection-2026-10-30-1310.md` (round 1) and `review-diff-004-fee-collection-2026-10-30-1421.md` (round 2). Fixes are listed in `docs/reports/004-fee-collection/fixes.md`.
- Reviewer: diff-reviewer agent, 2026-10-30 14:39.

## Verdict

All round-2 findings are fixed. The fix commit adds no new behaviour: one test assertion, one new test case, two doc wordings, a ui-tokens section and import reordering. Nothing new is wrong. `bash scripts/verify.sh` passed in full on HEAD `9b1c4f5`: backend, frontend build and unit tests, Playwright, and strict FR coverage all passed (`VERIFY OK`, exit 0).

| Id (round 2) | Status | Evidence |
|---|---|---|
| B1 | Fixed | `ReceiptCreateIT.java:110` now expects `"1 Oct 3601"` (TODAY is `3601-10-01`, line 35). The assertion still checks the date, so it was not weakened. `ReceiptCreateIT`: 8 tests, 0 failures. |
| M1 | Fixed | `openapi.yaml:39` and `FeeCollectionService.java:113` now say that a receipt number that is already used is checked only by the save and is refused beside the field after Confirm. This matches `ReceiptService` and US1 scenario 4. No other doc or frontend comment still says "never opens" (searched `specs/`, `management/`, `frontend/`). `ReceiptRules.java:19` ("the form never shows a number the save would refuse") is about the amounts, not the dialog, so it is still correct. |
| M2 | Fixed | `docs/design/ui-tokens.md:38-41` documents `--color-print-paper`, `--color-print-ink`, `--color-print-line` and `.link-target`. |
| M3 | Fixed | `FeeBalanceIT.java:279-281` adds the `receiptNo: ""` case with `validate=true` and expects 422 with field `receiptNo`. The replaced text `"receiptNo":"PV-1"` is in `complete` (line 258). If the replace did nothing, the call would return 200 and the test would fail, so the test can fail. |
| M4 | Fixed | `JsonMoney` is now in alphabetical order in all 11 ITs touched by the commit. |

Checks repeated on the whole branch, with no new problem:
- **Migrations**: `V8__receipts.sql` is the only migration in the diff. It is new on this branch; `main` has only V1 to V7, and none of those are changed.
- **Money**: no float or double was added. The fix commit changes no main-code logic, only the Javadoc.
- **Secrets and data**: `git ls-files` lists no `.env`, `credentials-first-run.txt`, `backups/` or `real-data/` file. The test dates (years 3601 and later) and the fixture values are synthetic.
- **History**: the reflog shows only normal commits since the F3 start, with no reset and no force-push.
- **Dependencies**: `package.json`, `package-lock.json` and `pom.xml` are unchanged on this branch.
- **FR tagging**: the commit message names FR-001, FR-008 and FR-018. The new test case sits inside the existing `@Covers` test.

Counts: 0 blocking, 0 should fix, 1 minor.

## Blocking

None.

## Should fix

None.

## Minor

**M1. One long Javadoc line**
- Location: `management/src/main/java/com/admin/management/fees/FeeCollectionService.java:113` (151 characters). The rest of that Javadoc wraps at about 120.
- Problem: Style only. No formatter enforces a limit, and about 20 other lines in main code are already longer than 130 characters.
- Fix: wrap the sentence "A receipt number already used is only checked by the save." onto its own line the next time the file is touched.

Carried over and not counted again: the duplicate `## Open items` section in `docs/decision-log.md` (round-1 M9). It was already on `main` and is deferred in `fixes.md`. Q11 and the day-wise panel row in the decision log are still "proposed". The caller did not ask for these to be decided in this round.

Outside this branch, for information only: `npm ci` reports 2 vulnerabilities (1 low, 1 high). This branch does not change any dependency, so it is not a finding here. It may need a separate check with `npm audit`.

## Verify result

This review ran `bash scripts/verify.sh` in full on 2026-10-30 (started 14:39, finished at about 14:57) on HEAD `9b1c4f5` with a clean tree and port 8080 free. Result: **VERIFY OK**, exit code 0.

- Backend `./mvnw -B verify`: 667 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS in 4 min 50 s. This includes `ReceiptCreateIT` (8 tests) and `FeeBalanceIT` (8 tests).
- Frontend: `npm ci` and `npm run build` succeeded. Unit tests: 34 files, 274 tests passed.
- Playwright (`npm run e2e`): 173 passed in 7.4 min. The `[WebServer] WARN ... violates ... constraint` lines in the log come from the tests that check refusals, which is expected, and are not failures.
- FR coverage (strict): 46 of 46 overall; 25 of 25 on branch `004-fee-collection`.
