Result: FIX FIRST

# Diff review: 004-fee-collection, round 2 (whole branch `main...HEAD`, after fix round 1)

- Branch: `004-fee-collection`, HEAD `7af1068`. The working tree was clean. The fix commits are `3464125`..`7af1068` (6 commits after `f6131c6`, 39 files, +391 / -132).
- Scope: the whole branch, with the focus on the fixes listed in `docs/reports/004-fee-collection/fixes.md` and on the four areas the caller named: `TableQueryService.stream` (cursor), the `validate=true` preview, the `numeric(12,2)` casts in `StudentTable`, and `JsonMoney`.
- Previous report: `review-diff-004-fee-collection-2026-10-30-1310.md` (FIX FIRST, 1 blocking, 4 should fix, 9 minor).
- Owner approval of the two constitution exceptions (`void_receipt`, Students list reading `v_fee_position` and `v_earlier_pending`) on 2026-10-30 is recorded in `docs/decision-log.md:72` and `:74`. Q11 (`:77`) and the day-wise panel row (`:83`) are still "proposed". As instructed, these are not treated as blocking.
- Reviewer: diff-reviewer agent, 2026-10-30 14:21.

## Verdict

The fixes do what they say, with one exception: the M7 fix (the duplicate-number message now shows "1 Oct 3601" instead of the ISO date) breaks an existing backend test that still expects the ISO date. `bash scripts/verify.sh` therefore fails: 667 backend tests, 1 failure. The push hook would refuse the push. This is a one-line test fix. Nothing else blocks.

Status of each round-1 finding:

| Id | Status | Notes |
|---|---|---|
| B1 | Fixed | Both exception rows say "approved by the owner 2026-10-30". |
| S1 | Fixed | No `value(<n>.<nn>)` money assertions remain in `management/src/test`. `JsonMoney` checks the exact text and scale. It also found and fixed a real FR-023 defect: list amounts without two decimals. |
| S2 | Fixed | `ModuleDependencyTest.java:102,110` exempts only `fees/FeeTestSupportController.java`. `security/TestSupportController` and `students/StudentTestSupportController` are scanned again, and the scan passes. |
| S3 | Fixed, with one gap (M1) | Save calls `preview?validate=true`, which runs the `complete` checks. The e2e test `saveRefused` asserts that no dialog opens. A duplicate receipt number still reaches the dialog. |
| S4 | Fixed | The single cursor query keeps the REPEATABLE_READ snapshot. `TableQueryStreamIT` (1,203 rows over 3 chunks, plus the concurrent-insert case) still passes. I ran `StudentListPerformanceIT` again: pass, 12.4 s for the whole test including the 10,000-row export. |
| M1 | Fixed | The dialog shows the server's `registration` and `fee`. |
| M2 | Fixed | Both alert checks now filter by the message text. |
| M3, M8 | Fixed | |
| M4 | Fixed | `--color-print-*` tokens are added. The theme-conformance e2e passes. See M2 below for the docs. |
| M5 | Fixed | `.link-target` (min-height `--spacing-control`). |
| M6 | Fixed | The FR-021 text now matches the code. |
| M7 | Fixed, but it broke a test | See B1. |
| M9 | Partly fixed | The spec status and the runbook note are done. The duplicate `## Open items` section is deferred, as `fixes.md` states. |

Areas checked with no new problem:
- **`TableQueryService.stream`** (`TableQueryService.java:83-94`)
  - Runs in the same `@Transactional(readOnly, REPEATABLE_READ)` as before. It is called through the Spring proxy from `TableEndpoints:68` and `ReceiptListController:51`, so the PostgreSQL cursor runs with autocommit off.
  - `org.hibernate.fetchSize` is the correct hint name. The stream is closed by try-with-resources.
  - The tuples hold scalars only, so the persistence context does not grow. `page()` still uses the offset query through `fetch()`.
  - The change applies to every export (audit log, master data, students, receipts), and all their export tests pass.
- **`validate=true` preview**
  - `@RequestParam(defaultValue = "false")` keeps the typing preview unchanged.
  - With validate, the preview runs the same `ReceiptRules.check(..., complete=true)` as the save. It is still read-only (`@Transactional(readOnly = true)`) and writes no audit entry.
  - `FeeBalanceIT.thePreviewReturnsTheCheckedAmountsAndValidateRefusesWhatTheSaveWouldRefuse` covers a future date, both amounts zero, missing cheque details, and the same input accepted without `validate`.
  - The contract (`openapi.yaml:36-42`, `:183-184`) and the TS type are updated.
- **`numeric(12,2)` casts** (`StudentTable.java:149,173,181`)
  - The sources are sums of `NUMERIC(12,2)` columns, so the cast never rounds.
  - NULL stays NULL, so "Fee not set" and the empty Paid value still work.
  - Sorting and filtering stay numeric.
  - In theory a sum of 10^10 or more would raise an overflow error instead of rounding. That cannot happen with real fees and is not a finding.
- **`JsonMoney`** (`support/JsonMoney.java`)
  - `USE_BIG_DECIMAL_FOR_FLOATS` plus `isBigDecimal()` means an integer `0` or `10500` fails the check, so the scale is really tested. A missing path also fails.
  - All 54 converted assertions pass. This shows that Jackson 3 keeps trailing zeros here: if it stripped them, `"0.00"` and `"10500.00"` could not match.
  - The pointer conversion handles only simple paths (`$.a.b[0].c`). That is documented in the method's Javadoc.
- **Constitution and never-do list**
  - No new migration, and `V8__receipts.sql` is unchanged since round 1. No float or double in main code.
  - No new write paths, so no new audit duties.
  - `git ls-files` lists no `.env`, `credentials-first-run`, `backups/` or `real-data/` files.
  - The reflog shows only normal commits: no reset or force-push.
- **Frontend**
  - No `any`, and the colour tokens come from `styles.css`.
  - `save()` no longer checks `previewBody()` and always sends the full input. The Cancel path and the 409 or 422 routing through `refused()` are unchanged.
- **FR coverage**: strict, 46 of 46 overall; 25 of 25 for this branch.

Counts: 1 blocking, 0 should fix, 4 minor.

## Blocking

**B1. The M7 fix breaks `ReceiptCreateIT`, so verify fails**
- Location: `management/src/main/java/com/admin/management/fees/ReceiptService.java:128` (message now uses `SCREEN_DATE`, for example "1 Oct 3601") versus `management/src/test/java/com/admin/management/fees/ReceiptCreateIT.java:110` (`containsString(TODAY)`, where TODAY is ISO "3601-10-01").
- Problem: `ReceiptCreateIT.aReceiptNumberUsedByAnotherReceiptIsRefusedNamingThatReceipt` fails: `Expected: a string containing "3601-10-01" but: was "This receipt number is already used by the receipt of 1 Oct 3601 (serial no. 10)."`. Backend: 667 tests, 1 failure, BUILD FAILURE. The verify run stops there. Commit `7af1068` was evidently not verified, and `fixes.md` does not record a verify run after the fixes.
- Fix: change the assertion to the screen form of the date (for example `containsString("1 Oct 3601")`, or format `TODAY` with the same pattern), then run `bash scripts/verify.sh` again and record the result in `fixes.md`.

## Should fix

None.

## Minor

**M1. "Validate" does not check a duplicate receipt number, but the docs say the dialog never opens for a refused receipt**
- Location: `specs/004-fee-collection/contracts/openapi.yaml:39` ("so the dialog never opens for a receipt the save would refuse"); the Javadoc at `FeeCollectionService.java:110-113` says the same. The duplicate check is only in `ReceiptService.java:120-131`, and the balance token is only checked on save.
- Problem: For a receipt number that is already used, the dialog still opens and the refusal comes after Confirm. Spec US1 scenario 4 and SC-003 are still met (nothing is saved, and the message is placed beside `receiptNo`), but the claim in the contract is wider than the behaviour.
- Fix: add a read-only duplicate lookup to the `validate` path (no lock needed, because the save still locks), or narrow the wording to "the field checks; a duplicate number and a changed balance are still refused on save".

**M2. `docs/design/ui-tokens.md` does not mention the new print tokens or `.link-target`**
- Location: `frontend/src/styles.css:38-41` (`--color-print-paper`, `--color-print-ink`, `--color-print-line`) and `:181-186` (`.link-target`). `docs/design/ui-tokens.md` has no print or receipt entry.
- Problem: The tokens doc is the reference for colours. The print-only black-on-white exception is recorded only in a CSS comment.
- Fix: add a "print sheet" row to the Colours table and a line on `.link-target` under "Type and shape".

**M3. The `validate=true` IT does not cover a missing receipt number**
- Location: `management/src/test/java/com/admin/management/fees/FeeBalanceIT.java:253-282`.
- Problem: It covers the date, zero amounts and cheque details. A missing `receiptNo` (also in the `complete` checks, and named in round-1 S3) is not tested through the preview. Other ITs cover it on the save path.
- Fix: add one `receiptNo: ""` case that expects 422 with the field `receiptNo`.

**M4. Import order of `JsonMoney`**
- Location: the converted ITs, for example `BalanceChangedIT.java:16`, `FeeListColumnsIT.java:20` and `StudentFeesBalanceIT.java:19`, put `support.JsonMoney` before `support.Covers`. `FeeBalanceIT.java:22` has it in order.
- Problem: Style only. No formatter enforces it.
- Fix: sort the imports when the files are next touched.

The duplicate `## Open items` section in `docs/decision-log.md` (round-1 M9) is still open. It was already on `main` and is deferred as stated in `fixes.md`, so it is not counted again here.

## Verify result

`bash scripts/verify.sh` was run by this review on 2026-10-30 (started 14:21, HEAD `7af1068`, clean tree). Result: **FAILED** in the backend stage.

- Backend `./mvnw verify`: 667 tests, 1 failure (`ReceiptCreateIT.aReceiptNumberUsedByAnotherReceiptIsRefusedNamingThatReceipt:110`, see B1), 0 errors, 0 skipped, BUILD FAILURE. Verify stopped here.

Because the script stopped, the remaining stages were run separately on the same HEAD:
- Frontend unit tests (`npm test -- --watch=false`): 34 files, 274 passed.
- Playwright (`npm run e2e`): 173 passed (7.2 min), including the new `saveRefused` cases and the theme-conformance checks.
- FR coverage (`scripts/check-fr-coverage.sh --strict`): 46 of 46 overall; 25 of 25 for `004-fee-collection`.
- Extra, not part of verify: `StudentListPerformanceIT` (tag `performance`) passed, 12.4 s for the whole test with 10,000 students, including the export.

After B1 is fixed, verify must be run again in full before the push.
