Result: APPROVED

# Self-review: 003-fees (F2 Fee structure and student fee accounts), round 2

Reviewer: independent strict review, 2026-10-28, branch `003-fees` at `b95adf2`.
Read: spec.md, plan.md, tasks.md, data-model.md, contracts/openapi.yaml, quickstart-results.md, constitution 1.1.0, CLAUDE.md, docs/decision-log.md (committed version at HEAD), every report in `docs/reports/003-fees/`, and `git diff main...HEAD` (41 commits, 138 files).

Since the round 1 self-review (`e555b5b`), only two commits were added: `16cef8d` (R1/R2 fix, 7 files) and `b95adf2` (test-results.md). I read all of `16cef8d` line by line, and re-read `FeeStructureService`, `FeeStructureController`, `fee-structure.component.ts/.html` and `fees.service.ts` in full. No other code changed, so the round 1 checks still hold for the rest of the branch (Money, migrations, no hard deletes, audit in the same transaction, no secrets, security, module boundaries). I did not run `scripts/verify.sh`. I rely on test-results.md: PASS, 829 (475 backend, 209 unit, 145 Playwright), strict FR coverage 14 of 14.

Ignored as instructed: the uncommitted owner edits to `docs/agreement/...v1.1...md` and `docs/decision-log.md` (F3 notes), and T030 (owner task).

Counts: 0 BLOCKING, 3 SHOULD FIX, 14 MINOR.

## Verification of the round 1 fixes

### Round 1 R1 (FR-001, inactive choices): fixed in the code; the contract is not updated (see R1 below)
- **Backend:** `FeeChoicesDto.java` gives `StandardChoice` and `CategoryChoice` an `active` flag. `FeeStructureService.java:85` and `:90` fill it from master data. The list is still "active, plus any inactive one held by a row of the year".
- **Frontend:** `fees.service.ts` adds `active` to `FeeChoices`. In `fee-structure.component.html:79` and `:92`, an option is shown only when it is active or is the row's current value. Add rows and other rows no longer offer the inactive item. This matches the FR-001 wording "not offered for new rows but stay on existing rows".
- **Server rule unchanged:** `validate` (`:227`, `:236`) still refuses an inactive item unless the row already holds it.
- **Tests:**
  - `FeeStructureReadIT` asserts `active` false and true per item.
  - `fee-structure.component.spec.ts` ("offers an inactive standard or category only in the row that holds it") checks the options of a held row and of a new row.
  - No test was weakened.
- **Small side effect:** see R4.

### Round 1 R2 (FR-013, deleted row gives 404): fixed
- **Service:** `FeeStructureService.editRow:109` and `deleteRow:124` now call `lockAndCheck` before `rowOfYear`. A screen holding an old structure version gets 409 `STALE_VERSION`, with the latest structure, before any 404.
- **Screen:** `fee-structure.component.ts` reloads on `StaleStructure` in `onSaveFailed:329` and in `runDelete:230`. The interceptor shows the toast. The admin now sees the latest values, as FR-013 requires.
- **Lock and rollback:** `lockAndCheck` creates and bumps the status row before the row lookup. A later 404 or 422 throws inside the same `@Transactional` method, so the bump is rolled back. Nothing leaks.
- **New test:** `FeeStructureIT.editingOrDeletingARowThatAnotherWindowDeletedAnswersStaleVersionNotNotFound` covers both PUT and DELETE.
- **Changed test, not weakened:** `aRowOfAnotherYearIsNotFound` now sends structure version `null`, which is the correct version for a year with no status row, and still asserts 404. Before the fix it sent `0` and only passed because the 404 came first.
- **Side effect:** see R5.

## Other checks

- **Tasks:** T001 to T029 are ticked with commits that carry FR ids. T030 is ticked but not done (R11, known).
- **No leftover code:** no TODO, FIXME, `console.log`, `System.out`, `printStackTrace`, `@Disabled`, `.only` or `.skip` was added on the branch (searched the added lines of the whole diff again).
- **test-results.md:** T1 to T3 are informational. T4 is T030 (owner). T5: SC-004 is automated in `FeeFinaliseIT` and `FeeReviseIT`, which are not tagged `performance` and run in `verify`. T6 is manual (look at the Excel file by eye). No finding needs a code fix.
- **Commits:** the fix commit message carries `FR-001, FR-013`.

## Findings

### R1. SHOULD FIX: the contract does not match the R1 fix or the Fee state column
- **Location:** `specs/003-fees/contracts/openapi.yaml:128-136` (`FeeChoices`) and `:105` (`/students/search`).
- **Problem:**
  - `FeeChoices` items still have no `active` property. Round 1 R1's fix said "contract, DTO, `fees.service.ts`", and only the DTO and the service were changed.
  - Line 105 says `feeState` is "NOT_SET or null" and "No ... fee-status columns". Since `ddfda3d`, the column holds readable text ("Fee not set" or "Not finalised"), per the 2026-10-28 decision (`StudentTable.java:146-149`).
- **Fix:** add `active: { type: boolean }` to both choice item schemas, and reword line 105 to the text values and the hidden Fee state column.

### R2. SHOULD FIX: spec FR-010 and US2 scenario 7 still contradict the 2026-10-28 owner decision (round 1 R3, open)
- **Location:** `specs/003-fees/spec.md:57` and `:152`.
- **Problem:**
  - The spec says the state text is in the Total fee column "also in the Excel export", and that there are "no ... fee-status columns".
  - What was built (decision log row 37): in the export, Total fee is blank and a separate "Fee state" text column holds the state, hidden on screen.
- **Fix:** reword FR-010 and scenario 7, or add a Clarifications line that points to the 2026-10-28 decision.

### R3. SHOULD FIX (unsure): money passes through doubles in the Excel export and the browser, and the plan's Constitution Check does not record it (round 1 R4, open)
- **Location:**
  - `management/src/main/java/com/admin/management/shared/excel/ExcelExporter.java` (`writeMoney`, `doubleValue()`).
  - `frontend/src/app/shared/ui/money.ts:21` and `fee-structure.component.ts:321` (amounts sent as JS numbers).
  - `specs/003-fees/plan.md:36` and `:133` (Constraints say "no float or double for money anywhere, including tests"; Complexity Tracking has no entry).
- **Problem:**
  - Principle I covers "tests and exports".
  - The 2026-10-26 decision approves numeric Excel cells but does not mention Principle I or doubles.
  - I cannot tell whether the owner sees this as an approved exception.
- **Fix:** have the owner record the exception in plan.md's Complexity Tracking (or as a PATCH clarification of Principle I).

### R4. MINOR: a row switched away from its inactive standard or category cannot be switched back (side effect of the R1 fix)
- **Location:** `frontend/src/app/features/fees/fee-structure.component.html:79` and `:92`.
- **Problem:** the condition compares with the form's current value, not the saved row's value. If a row holds inactive "Old std" and the admin picks "10" by mistake, "Old std" disappears from that row's list. Only Reload (or Stop revising) brings it back. The server would accept the original value.
- **Fix:** keep the saved `standardId` and `categoryId` on `RowEditor` (from `editorOf`) and compare with those.

### R5. MINOR: row path edge cases after the R2 fix, and contract gaps
- **Location:** `FeeStructureService.java:109-110` and `:124-125`; `openapi.yaml:43-65`.
- **Problem:**
  - In a Finalised year, a PUT or DELETE for a row id that does not exist or belongs to another year (API only; the screen cannot send it) now returns `REVISE_CONFIRMATION_REQUIRED` first, and the 404 only after `confirm=true`. Nothing is saved.
  - The contract lists no 404 for PUT and DELETE `/rows/{rowId}`.
  - The contract marks the DELETE parameters `structureVersion` and `version` `required: true`. The controller takes both as optional, and null `structureVersion` means "no status row yet". The changed test now omits `structureVersion`.
- **Fix:** add `404` to both row operations, and mark both DELETE parameters as optional and nullable in the contract. Optionally do a cheap row-exists check before the confirmation check.

### R6. MINOR: the money box can silently round extreme input (round 1 R5, open)
- **Location:** `frontend/src/app/shared/ui/money.ts:21`.
- **Problem:** `1.000000000000000001` becomes `1` and is saved as 1.00 instead of being refused (FR-011).
- **Fix:** use `^-?\d{1,10}(\.\d+)?$` and send the cleaned string, so the server refuses the extra decimals.

### R7. MINOR: dead `busy` input in the revise dialog (round 1 R6, open)
- **Location:** `frontend/src/app/features/fees/revise-dialog.component.ts:41` (used at `:16`, `:27`, `:30`; no caller binds it).
- **Fix:** remove it.

### R8. MINOR: Brought forward section: no loading line, and the error line has no unit test (round 1 R7, open)
- **Location:** `student-form.component.html:236-270`.
- **Problem:** `brought-forward-error` is not referenced in any `*.spec.ts`.
- **Fix:** add a loading line and a unit test for the error line.

### R9. MINOR: `feeText` hard-codes the server text (round 1 R8, open)
- **Location:** `frontend/src/app/features/students/student-list/student-columns.ts:87`.
- **Fix:** show `row['feeState']` when it is a string; fall back to "Not finalised" only when it is null.

### R10. MINOR: quickstart-results.md is out of date (round 1 R9, open)
- **Location:** `specs/003-fees/quickstart-results.md:11-13`, `:40` and `:49`.
- **Problem:**
  - The counts are 430, 201 and 138; now they are 475, 209 and 145.
  - Row 19 says the finalise and revise timings are "tagged `performance`, not part of `verify`". They are not tagged and do run in `verify`.
  - The T028 line says "decision-log: no change", but the 2026-10-28 row was added.
- **Fix:** update the three points.

### R11. MINOR (known): T030 is ticked but not done
- **Location:** `specs/003-fees/tasks.md:129`; `quickstart-results.md:55-57` (blank).
- **Fix:** the owner records the time and result before `/push-feature`, or unticks the box until then.

### R12. MINOR: duplicated test helper `awaitBlockedSession` (round 1 R11, open)
- **Location:** `FeeClassRecordIT.java:86`, `FeeFirstSaveRaceIT.java:119`, `FeeLockIT.java:209`.
- **Fix:** move it to `src/test/java/com/admin/management/support/`.

### R13. MINOR: main code used only by tests (round 1 R12, open)
- **Location:**
  - `FeeSelection.Result.isSet()` and `totalFee()`.
  - The `FeeYearStatus(Long)` and `BroughtForward(...)` constructors.
  - `getUpdatedAt()` on `FeeAccount` and `BroughtForward`.
- **Fix:** remove them, or test through the production paths.

### R14. MINOR: in a revise, the warning comes before validation (round 1 R13, open)
- **Location:** `FeeStructureService.java:97-98` and `:109-112`.
- **Problem:** an invalid value (for example -1) in a Finalised year first shows the recalculation warning. The field error appears only after the admin confirms.
- **Fix:** run the field checks of `validate` after the stale check and before the confirmation check.

### R15. MINOR (unsure): a narrow race when the year has no status row (round 1 R14, open)
- **Location:** `FeeClassRecordHooks.classRecordAdded` and `lockedForChange`.
- **Problem:** with no status row, the share lock locks nothing. Not proven by a test. With one admin it is practically unreachable.
- **Fix:** note it in research.md, or lock the academic year row in both paths.

### R16. MINOR: a decision-log open item is not updated (round 1 R15, open; checked against the committed HEAD version)
- **Location:** `docs/decision-log.md:42`, "Audit masking for money fields".
- **Fix:** add "Fees: settled by F2 FR-012 (amounts in full); salary still open."

### R17. MINOR: `GET /students/{id}/fees?yearId=` answers NOT_FINALISED for an unknown year or a year with no class record (round 1 R16, open)
- **Location:** `management/src/main/java/com/admin/management/fees/StudentFeesService.java:49-55`.
- **Fix:** return 404 in those cases, or drop `yearId` from the contract (the screen never sends it).

## Unsure points (not guessed)

- R3: whether the owner considers double-valued Excel cells and the JS-number transport a Principle I exception that must be written into the plan.
- R15: whether the race can happen in practice. No test proves it either way.
- I did not rerun the suites. The pass counts are from test-results.md.
- `docs/reports/003-fees/fixes.md` lists the diff-review fixes only. I could not find a rule saying the self-review R1/R2 fixes must be recorded there, so this is not raised.
