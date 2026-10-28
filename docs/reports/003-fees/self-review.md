Result: APPROVED

# Self-review: 003-fees (F2 Fee structure and student fee accounts)

Reviewer: independent strict review, 2026-10-28, branch `003-fees` at `2579c8b`, clean tree.
Read: spec.md, plan.md, tasks.md, data-model.md, contracts/openapi.yaml, quickstart-results.md, constitution 1.1.0, CLAUDE.md, docs/decision-log.md, every report in `docs/reports/003-fees/`, and `git diff main...HEAD` (39 commits, 137 files). I did not run `scripts/verify.sh`. I rely on test-results.md (PASS: 474 backend, 208 unit, 145 Playwright, strict FR coverage 14 of 14 on the branch).

Known and intentional, not raised as blocking: T030 (owner's timed check, recorded before push) and the 2026-10-28 decision-log row (Fee state column in the Excel export).

Counts: 0 BLOCKING, 4 SHOULD FIX, 12 MINOR.

## Checks that pass

- **Tasks.** T001 to T029 are ticked and have commits with FR ids. The only exception is `d80a3d4` ("feature 3 task"), noted as round 1 M10. T030 is ticked but not done yet (R10, known).
- **No leftover code.** No TODO, FIXME, `console.log`, `System.out`, `printStackTrace`, `@Disabled`, `test.only`/`skip`/`fixme` or commented-out code was added on the branch. The throwaway tests of T002 and T003 were deleted as planned.
- **test-results.md.** T1 to T3 are informational. T4 (scenario 20) is the owner's T030. T5 is answered: SC-004 is automated and runs in `verify`, because the 2,000-record finalise and revise tests in `FeeFinaliseIT.java:185-191` and `FeeReviseIT.java:205-210` are not tagged `performance`. T6 (look at the Excel file by eye) is manual. The numeric cell is asserted in `StudentListIT` and `fees-student-views.spec.ts`.
- **Round 1 and 2 fixes.** Round 1 fixes S1, S2, S4 and M1, M2, M4 to M9 are in the code, as round 2 checked. I re-read `BroughtForwardService.save`, `FeeStructureService.lockAndCheck` and `FeeYearStatusRepository.insertIfAbsent`. No test was weakened or deleted:
  - `FeeRepositoryIT` swapped one repository method for an equivalent one.
  - F1 tests that banned the word "Fee" (`StudentProfileIT`, `students-shell.spec.ts`, `students-list-profile.spec.ts`) now ban `"fee"`, `totalFee`, receipt, paid and pending instead. That is narrower, but correct for F2.
  - `ExcelExporterTest`, `TableExportIT` and `StudentListIT` moved from text to numeric cells, as T006 required.
- **Money.** Every backend amount is `BigDecimal` with `precision 12, scale 2`. `Money.scale2` uses `RoundingMode.UNNECESSARY`. More than two decimals, a negative amount or more than 10 integer digits is refused, not rounded (`FeeStructureService.moneyError`, `BroughtForwardService.validate`). Totals only use `add`. The database has `NUMERIC(12,2)` and CHECK `>= 0`. See R4 for the export and the browser.
- **Migrations.** Only `V7__fees.sql` is new. V1 to V6 are untouched. The constraints match data-model.md. Every foreign key is `ON DELETE RESTRICT`.
- **No hard deletes.** `FeeAccountRepository` and `BroughtForwardRepository` have no delete method. "Clear" saves 0. Fee rows can be deleted only as setup data, with an audit entry (plan, Principle IV). The `DELETE FROM` statements are only in `FeeTestSupportController`, which is `@Profile("test")` and behind `TestProfileGuard`.
- **Audit in the same transaction.** `FeesAudit` writes through `AuditService.record` (MANDATORY) inside the `@Transactional` service methods:
  - Row create, edit and delete; Revise with `studentsRecalculated` and `accountsChanged`.
  - Finalise with `rows`.
  - Brought forward with the amount in full and only `noteChanged`, never the note text.
  - A no-op save writes no entry.
- **No secrets or real data.** Fixtures are synthetic (`Seedsurname`, `RaceBf`, Gujarati note test text). No `.env`, `credentials-first-run.txt`, `backups/` or `real-data/` is tracked.
- **Security.**
  - Every new endpoint is under `/api/**` and needs a session.
  - `FeeAccessIT` checks 401 without a session and 403 without CSRF on every fee endpoint. It also checks that no endpoint sets or deletes a per-student fee or account.
  - Native SQL uses bound or named parameters. The two `formatted` SQL strings take constants or ids read from the database. One is `StudentTable.SHOWN_RECORD`; the other is the seed endpoint, test profile only.
  - Error messages are plain sentences with no internals.
- **Modules.** `fees` reads students only through `students.api` (`EnrollmentLookup`, `StudentLookup`). `students` reaches fees only through `shared.ClassRecordFees`. The one cross-module table read (`StudentTable` reading `fee_accounts`) is named in the plan's Complexity Tracking, the decision log and the `ModuleDependencyTest` table-scan allow-list.
- **FR behaviour checked against the spec text.** FR-002, FR-003, FR-004 (warning count = class records including Left students), FR-005, FR-006, FR-007, FR-008 (message text matches; stale check, then lock, then validation), FR-009, FR-011 and FR-012 match. Exceptions are R1 (FR-001), R2 (FR-013) and R3 (FR-010 text).
- **Docs.** CLAUDE.md "no change needed" is correct: no new command, and the test endpoints are test-only. The decision log keeps every row from `main` and adds the 2026-10-26 and 2026-10-28 rows. The ui-prototype change log and the feature-workflow status are updated. Agreement section 11 and the 4.4 note are present.

## Findings

### R1. SHOULD FIX: inactive standards and categories are offered for new rows (FR-001)
- **Location:** `management/src/main/java/com/admin/management/fees/FeeStructureService.java:83` and `:89`; `frontend/src/app/features/fees/fee-structure.component.html:78-80` and `:89-91`; contract `FeeChoices` (no `active` field).
- **Problem:** `/choices` returns active items plus any inactive standard or category that an existing row holds, with no flag. The same list fills the drop-downs of every row, including an Add row. The spec says inactive items are "not offered for new rows". Today they are offered and then refused by the server ("This standard is not active.").
- **Fix:** add `active` to the choice items (contract, DTO, `fees.service.ts`) and list inactive items only in the row that already holds them.

### R2. SHOULD FIX: editing or deleting a row deleted in another window gives 404, and the screen does not reload (FR-013)
- **Location:** `FeeStructureService.java:109-110` and `:124-125` (`rowOfYear` runs before `lockAndCheck`); `fee-structure.component.ts`, `onSaveFailed` and `runDelete` error branches.
- **Problem:** FR-013 says a row change sent after the row or the structure changed "is refused with the latest values shown". When the row was deleted elsewhere, the structure version is stale, but the 404 from `rowOfYear` is thrown first. The screen only reloads on `StaleStructure`, so the admin stays on the old table. No test covers this case.
- **Fix:** call `lockAndCheck` (the structure version check) before `rowOfYear` in `editRow` and `deleteRow`, so the answer is STALE_VERSION with the latest structure. Add a test in `FeeStructureIT`.

### R3. SHOULD FIX: spec FR-010 and US2 scenario 7 still contradict the 2026-10-28 owner decision (round 2 M3, still open)
- **Location:** `specs/003-fees/spec.md:57` and `:152`.
- **Problem:** The spec says "Fee not set" or "Not finalised" goes in the Total fee column, "also in the Excel export", and that there are "no fee-status columns". What was built follows the decision log instead: in the export, Total fee is blank and a separate "Fee state" text column holds the state, hidden on screen. The constitution says specs must not contradict a recorded decision.
- **Fix:** reword FR-010 and scenario 7, or add a Clarifications line that points to the 2026-10-28 decision.

### R4. SHOULD FIX (unsure): money passes through IEEE doubles in the Excel export and in the browser, but the constitution check does not say so
- **Location:**
  - `management/src/main/java/com/admin/management/shared/excel/ExcelExporter.java:61-71` (`writeMoney`, `amount.doubleValue()`).
  - `frontend/src/app/shared/ui/money.ts:21` and `fee-structure.component.ts`, `inputOf` (amounts sent as JS numbers).
  - Tests compare doubles: `ExcelExporterTest.java:67`, `:76`, `:79`; `StudentListIT` (`isEqualTo(28500.0)`).
- **Problem:** Principle I says float and double "MUST NOT hold or compute money anywhere, including tests and exports". An xlsx numeric cell is a double by format, and the code only writes it when the double reads back exactly. The owner approved numeric cells (decision log 2026-10-26; plan decision 10 says the approval was "assumed", plan review Q3). The JS-number transport is plan research R3. However, plan.md's Constitution Check row I says "no float or double for money anywhere, including tests", and Complexity Tracking has no entry for either point. I am not sure the owner sees this as a violation. It needs an explicit record rather than a guess.
- **Fix:** add both points to plan.md's Constitution Check and Complexity Tracking with the owner's approval, or have the owner approve a PATCH clarification of Principle I for export cells and browser transport.

### R5. MINOR: the money box can still silently round extreme input (round 2 M4, open)
- **Location:** `frontend/src/app/shared/ui/money.ts:21`.
- **Problem:** `^-?\d+(\.\d+)?$` accepts any length, so `1.000000000000000001` becomes `1` and is saved as 1.00 instead of being refused (FR-011).
- **Fix:** use `^-?\d{1,10}(\.\d+)?$` and keep all decimals for the server to refuse, or send the cleaned string.

### R6. MINOR: dead `busy` input in the revise dialog (round 2 M1, open)
- **Location:** `frontend/src/app/features/fees/revise-dialog.component.ts:41` (used at `:16`, `:27`, `:30`).
- **Fix:** remove the input and its three uses.

### R7. MINOR: the Brought forward section has no loading line, and its error line is untested (round 2 M2, open)
- **Location:** `frontend/src/app/features/students/student-form/student-form.component.html` (brought-forward section) and `student-form.component.ts:172-182`.
- **Fix:** show a loading line while it is not ready and not failed, and add a unit test for `brought-forward-error`.

### R8. MINOR: `feeText` hard-codes the server's text "Fee not set" (round 2 M6, open)
- **Location:** `frontend/src/app/features/students/student-list/student-columns.ts` (`feeText`).
- **Fix:** show `row['feeState']` when it is a string; fall back to "Not finalised" only when it is null.

### R9. MINOR: quickstart-results.md is out of date
- **Location:** `specs/003-fees/quickstart-results.md:11-13` and `:40`.
- **Problem:**
  - The T027 counts (430, 201, 138) predate the gap-filling tests (474, 208, 145).
  - Row 19 says the finalise and revise timings are "tagged `performance`, not part of `verify`". Only `StudentListPerformanceIT` is tagged; `FeeFinaliseIT` and `FeeReviseIT` run in every `verify`.
  - The list timings were measured before the `feeState` SQL change (round 2 M5).
- **Fix:** update the counts and the row 19 wording, and rerun `StudentListPerformanceIT` once.

### R10. MINOR (known): T030 is ticked but not done
- **Location:** `specs/003-fees/tasks.md:129`; `quickstart-results.md:55-57` (Time and Result are blank).
- **Fix:** the owner records the time and pass or fail before `/push-feature`. Until then the box should be unticked, or say "pending owner".

### R11. MINOR: duplicated test helper
- **Location:** `awaitBlockedSession` is copied in `FeeClassRecordIT.java:86`, `FeeFirstSaveRaceIT.java:119` and `FeeLockIT.java:209`.
- **Fix:** move it into `management/src/test/java/com/admin/management/support/` and filter on the query text (round 2 M7).

### R12. MINOR: main code used only by tests
- **Location:**
  - `FeeSelection.Result.isSet()` and `Result.totalFee()`.
  - The `FeeYearStatus(Long)` and `BroughtForward(...)` constructors (production uses `insertIfAbsent`).
  - `FeeAccount.getUpdatedAt()` and `BroughtForward.getUpdatedAt()`.
- **Fix:** remove them, or have the tests use the production paths.

### R13. MINOR: in a revise, the warning comes before validation
- **Location:** `FeeStructureService.java:97-98` and `:110-112`.
- **Problem:** An invalid change to a finalised year (for example amount -1) first shows the recalculation warning. The field error appears only after the admin confirms. Nothing wrong is saved, but the admin confirms something that will then be refused.
- **Fix:** run `validate` after the stale check and before the confirmation check.

### R14. MINOR (unsure): a narrow race when the year has no status row yet
- **Location:** `FeeClassRecordHooks.classRecordAdded` and `lockedForChange`.
- **Problem:** When `fee_year_status` has no row, `lockForShare` locks nothing. A registration running while another window adds the first row and finalises within the same moment would commit a class record with no account. With one admin this is practically impossible, and I have not proven it with a test.
- **Fix:** either accept it and note it in research.md, or lock the academic year row (through masterdata) in both paths.

### R15. MINOR: a decision-log open item is not updated
- **Location:** `docs/decision-log.md`, open item "Audit masking for money fields".
- **Problem:** The spec's Assumptions say fees are settled by FR-012 (amounts in full), but the open item still reads as fully open.
- **Fix:** add "Fees: settled by F2 FR-012 (amounts in full); salary still open."

### R16. MINOR: `GET /students/{id}/fees?yearId=` gives misleading answers
- **Location:** `management/src/main/java/com/admin/management/fees/StudentFeesService.java` (`fees`).
- **Problem:** With an unknown `yearId`, or a year in which the student has no class record, the answer is `NOT_FINALISED` ("Fee structure not finalised") even if that year is finalised. An unknown year gives no 404. The screen never sends `yearId`.
- **Fix:** return 404 for an unknown year or for no class record in that year, or remove the parameter from the contract.

## Unsure points (not guessed)

- R4: whether the owner considers double-valued Excel cells and JS-number transport a Principle I violation that needs a written exception.
- R14: whether the race is reachable in practice. No test was written to prove it.
- I did not rerun the suites. The pass counts are from test-results.md.
