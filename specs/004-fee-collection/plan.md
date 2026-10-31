# Implementation Plan: Fee Collection and Receipts (F3)

**Branch**: `004-fee-collection` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-fee-collection/spec.md` (with the three clarify answers and the owner's answers to the plan's open questions, all of 2026-10-28)

## Summary

F3 adds receipts to the `fees` module: the admin records a current-year receipt (registration fee row and year fee row) or a
previous-years receipt (against the brought-forward amount), confirms it in a dialog, and prints it on A4. Paid, pending and
"Paid up to" are always worked out from the valid receipts. A cheque can be marked "Cheque not cleared" (one way, audited). A
receipt row is never updated or deleted: the mark is a separate append-only record, and the database refuses an update or
delete of a receipt or a mark. The Students list and the profile gain Paid, Pending and "Paid up to" (the date lives only there, not on any receipt); a receipt register
with an Excel export (day-wise totals) is added under a new Finance tab.

It reuses F0, F1 and F2 unchanged where it can: session and CSRF, `ApiExceptionHandler` and `CodedConflictException`,
`AuditService` in the caller's transaction, `TableDef` / `ColumnDef` / `TableEndpoints` and the Excel exporter, `MasterDataLookup`,
`students.api`, `Money`. Only what is new is described here; the reasons are in [research.md](research.md).

**Prerequisite**: F2 (`003-fees`, migration `V7`) is merged into `main` and this branch is up to date with it. This branch is created
from `main` at `6fa7d7e`, which contains the F2 merge `62cfabb`.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2, Maven; TypeScript strict, Angular 22 standalone with signals (unchanged)

**Primary Dependencies**: no new library. Money is `BigDecimal` with `NUMERIC(12,2)`. The Excel export uses the existing POI exporter. Printing uses CSS `@media print` and `@page { size: A4 }` (no PDF library, research R10).

**Storage**: PostgreSQL (15 is carried from the F0 plan; `deploy/docker-compose.yml` is commented out and mentions 16, so the version is checked in the first tasks and nothing here depends on a version-specific feature). New migration `V8__receipts.sql` (V1 to V7 are not edited): tables `receipts` and `receipt_marks`, views `v_valid_receipts`, `v_fee_position` and `v_earlier_pending`, the immutability triggers, the receipt-number trigger and the developer function `void_receipt`. No change to the audit table (its action list already has Create, Edit and Export).

**Testing**: JUnit 5 `@SpringBootTest` with `@Covers("FR-xxx")`; pure unit tests for paid-up-to, amount in words and validation; integration tests including two simultaneous saves, the immutability triggers and the audit rows; Angular unit tests; Playwright with FR ids in titles; `scripts/check-fr-coverage.sh --strict`.

**Target Platform**: Windows 10, 4 GB RAM, one PC (checked in F11).

**Project Type**: web application (existing `management/` and `frontend/`).

**Performance Goals**: the Students list, with the new money columns, stays under 2 s on 10,000 students (F1 SC-003); the receipt register pages, filters and sorts under 2 s on 10,000 receipts and exports them in under 30 s (spec SC-008, same limits as F1). A measurement task is planned (the performance spike in the first tasks, with 10,000 students; the real tests use 2,000 students and 10,000 receipts as spec SC-008 says; both are kept).

**Constraints**: constitution I to VIII; offline; one admin; strictest checks on money: no `float` or `double` anywhere including tests, `compareTo` for comparisons, every amount refused (never rounded) beyond two decimals, a database CHECK on every money invariant.

**Scale/Scope**: one school, about 2,000 students, about 10,000 receipts a year; 6 stories, 25 FRs, 3 new screens (collect fee, receipt view with print, receipt register) and changes to 3 existing screens (profile, Students list, shell).

## Constitution Check

*GATE: passes before research. Re-checked after design at the end of this file: passes, with one extension of the Principle VII exception (Complexity Tracking) that needs the owner's recorded approval before the students-list tasks start.*

| Principle | Result |
|---|---|
| I. Exact Money Arithmetic | `BigDecimal` and `NUMERIC(12,2)` for every amount; CHECK `>= 0` and `total = registration_paid + fee_paid`; more than two decimals refused (F2 `Money`, FR-023). Division only in "Paid up to", with one `HALF_UP` step at scale 0 (research R4). The frontend never adds money; every total comes from the server (F2 R3). Tests use `BigDecimal`. |
| II. Immutable Finalized Records | A saved receipt is final: `@Immutable` entity (no update statement), a database trigger rejecting `UPDATE`, `DELETE` and `TRUNCATE` on `receipts` and `receipt_marks`, and no endpoint to change one. "Cheque not cleared" is a reversal entry (a row in `receipt_marks`), not an edit, which is what Principle II asks for. The developer correction is also a reversal entry (`void_receipt`, research R9) followed by a new receipt. The principle's example "receipt cancellation with a reason" is replaced for the user by these two reversals under the owner's decision of 2026-10-28, who confirmed the separate-record design and the void; no amendment is needed. |
| III. Audited Financial Writes | `AuditService.record` (`MANDATORY`) in the same transaction for a receipt and for a mark; the developer void writes its audit row in the same transaction inside `void_receipt`; the export uses `recordIndependent` through `TableEndpoints`, as F0 and F1 do, after the file is produced. A print writes nothing: it is not a financial write and the owner decided that prints are not recorded (research R8). A failed audit entry rolls the receipt back. |
| IV. No Hard Deletes of People | Nothing about a student is deleted. All foreign keys are `ON DELETE RESTRICT`. A left student keeps their receipts and can still pay (FR-007). |
| V. Flyway-Only Schema Changes | One new migration `V8`; `ddl-auto=validate`; no manual schema change. The developer function and the views are in the migration. The one manual **data** change is the developer's call of `void_receipt` through `psql` (R9); principle V says manual changes to a database are prohibited, so this is named in Complexity Tracking and recorded in the decision log (first task), with its safeguards. |
| VI. No Real Data in the Repo | Synthetic students, receipt numbers and amounts only. The school name in the print header comes from configuration (`campusflow.school.name`), not from the code; address and phone are optional and empty for now. Cheque numbers and bank names are not sensitive fields (they are not account numbers); the audit masking list does not change (research R7). |
| VII. Modular Monolith | `receipts` live in `fees`. `fees -> students.api` (adds `StudentLookup.summary`, `ClassRecordRef.divisionId`), `fees -> masterdata.api` (adds year dates), `fees -> audit`, `fees -> shared`; `students` reaches fees only through `shared` interfaces and the read-only list-column exception. Acyclic order `shared <- audit <- masterdata <- students <- fees` unchanged. The Students list reads two **views** (not tables) owned by `fees`: the exception is extended from tables to these views, see Complexity Tracking; the decision-log row is written before the Students list task. |
| VIII. Every Requirement Is Tested | `@Covers` and Playwright titles for FR-001 to FR-025; reference cases for "Paid up to" (including leap year, zero fee, full payment, cheque not cleared) and for the amount in words; strict FR coverage check in the last task. |

## Project Structure

### Documentation (this feature)

```text
specs/004-fee-collection/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/openapi.yaml
└── tasks.md             # /speckit-tasks, not written here
```

### Source Code (changes only)

```text
management/src/main/resources/db/migration/V8__receipts.sql
management/src/main/java/com/admin/management/
├── masterdata/api/MasterDataLookup      # CHANGE: yearDates(yearId), yearDatesByYear(), earlierYears(beforeStart) (start and end dates; the module already owns them)
├── students/api/StudentLookup           # CHANGE: summary(studentId) -> name, GR no., status (for the receipt snapshot)
├── students/api/EnrollmentLookup        # CHANGE: ClassRecordRef gains divisionId (additive; F2 callers updated)
├── students/StudentTable                # CHANGE: columns Paid, Pending, Earlier years pending, Paid up to, Fee status (read-only, views of fees)
├── shared/web/ApiExceptionHandler       # CHANGE: KNOWN gains uq_receipts_receipt_no, the constraint name the receipt-number trigger raises (field receiptNo)
├── shared/table/TableEndpoints          # CHANGE: export accepts an optional extra (row observer plus a second sheet) for the day-wise totals
├── shared/excel/ExcelExporter           # CHANGE: writes the extra sheet after the main one; tests updated
└── fees/
    ├── Receipt, ReceiptMark                               # NEW entities (@Immutable)
    ├── ReceiptRepository, ReceiptMarkRepository
    ├── FeeBalance, FeeBalanceService                      # NEW: balance of a student worked out from the valid receipts (views), balance token
    ├── PaidUpTo                                           # NEW pure rule (research R4)
    ├── AmountInWords                                      # NEW pure utility (research R6)
    ├── ReceiptService                                     # NEW: create (current-year and previous-years), lock, validate, audit
    ├── ReceiptMarkService                                 # NEW: mark Cheque not cleared
    ├── ReceiptTable                                       # NEW: register columns (TableDef)
    ├── DayTotals                                          # NEW: day-wise totals from the streamed rows
    ├── ReceiptController, ReceiptListController, StudentFeeCollectionController   # NEW
    ├── SchoolProperties                                   # NEW: campusflow.school.name (required), address and phone (optional)
    ├── FeesAudit                                          # CHANGE: receiptCreated, chequeNotCleared
    ├── BroughtForwardService                              # CHANGE: refuses an amount below the valid previous-years total (FR-025), takes the student lock
    └── StudentFeesService, StudentFeesDto                 # CHANGE: paid, pending, paid up to, receipts (FR-014)
management/src/test/java/com/admin/management/shared/ModuleDependencyTest   # CHANGE: allowed table reads (research R11)
docs/runbooks/correct-receipt.md                          # NEW (FR-022)
scripts/                                                  # no new script; the runbook calls the migration's function

frontend/src/app/
├── core/layout/shell.component                  # CHANGE: tab Finance with the pill Fee collection
├── features/finance/                            # NEW
│   ├── finance.routes.ts, receipts.service.ts
│   ├── receipt-list/                            # register, filters, day-wise totals, export
│   ├── receipt-form/                            # student search, type, number, date, amounts, mode, live total
│   ├── receipt-confirm-dialog/                  # student, number, date, amounts, total (FR-008)
│   ├── balance-changed-dialog/                  # "the balance has changed", Refresh (FR-016)
│   ├── receipt-view/                            # details, status, Mark not cleared dialog, Print
│   └── receipt-print/                           # the one component holding the A4 layout and the school header
├── features/students/student-profile/           # CHANGE: Fees block gains paid, pending, paid up to, receipts, Collect fee
└── features/students/student-list/student-columns.ts   # CHANGE: the new columns and the Fees status filter
frontend/e2e/                                    # NEW receipt specs; fees-student-views updated
```

**Structure Decision**: same two-folder layout as F0 to F2. The prototype's Finance tab appears now, with one pill (Fee collection); Fee tracker, Payroll and Expenses stay hidden until their features exist.

## Decisions that differ from the brief or that need a flag

The brief (items 1 to 14) was followed except where it conflicts with the spec. In each case the spec was followed, the reason is in research.md, and the owner is asked in the open questions.

1. **Receipt row never changes (brief item 2 vs FR-009).** The brief lets `status` and the `unclear_*` columns change on the receipt row. The spec and Principle II keep the row unchanged, so `receipts` has no status column and the mark is a separate append-only table `receipt_marks` (R1). The trigger refuses every update, so "only the status columns may change" is not needed. *Owner confirmed (Q2).*
2. **Balances are computed, not stored (brief item 3 vs FR-016).** The brief recommends paid totals on the fee account. The spec says the balance is never from a stored running total. Paid and pending come from the valid receipts through the view `v_fee_position`; an integrity test recomputes the balance independently in Java from the receipts and compares (R2). A balance token (not a money total) carries the stale check (R3). *Owner confirmed (Q5).*
3. **"Paid up to" is not on the receipt (owner answer to Q4; brief item 6 and FR-012 as first written).** It is a figure of the student, shown on the profile and in the Students list beside the remaining fees, always worked out from the current valid receipts, never stored. It is the last paid day: start date + days paid - 1, one division at scale 0 with `HALF_UP` (spec FR-012), so no cap is needed; "Fully paid" and "Nothing paid" are states, not dates (R4). This removes the stored date, the print line and the register column.
4. **Previous-years receipts carry no "Paid up to"** (brief item 5, FR-004). F2 holds one brought-forward amount and no per-year fee, so no date can be supported; the earlier year is a label (R5). *Owner confirmed the planned balances (Q6, Q7).*
5. **Audit masking is not changed (brief item 4).** Money field names are not in the masked list, so they are already stored in full; a test proves it for the Fees area and the decision-log row records the rule (R7).
6. **Developer correction is a void (owner answer to Q3).** `void_receipt` adds a `DEVELOPER_VOID` mark and an audit row; the receipt row stays as it was; the voided receipt's number is free again and the admin enters the corrected receipt, which may use that number (R9, R12).
7. **Receipt number is unique across all receipts, voided ones excepted** (owner answer to Q1, FR-001). Case-insensitive after trimming, enforced by the service and by a database trigger, because a plain unique index cannot ignore voided receipts (R12).
8. **No DUPLICATE mark and no print record** (owner answer to Q8, FR-018). A re-print is identical to the first print. This changes agreement section 8 ("Duplicate copies are marked DUPLICATE"); a decision-log row and an agreement section 11 line are written in the first (decisions) task, and the print table, the print endpoint and the print service of the first plan are dropped (R8).

## Differences from the prototype

The collect-fee screen follows `docs/design/ui-prototype.md` except for these owner decisions:

- Registration fee and year fee are separate rows, each with its remaining due; a previous-years receipt has one row for the earlier year.
- No "Need to cancel a receipt?" link anywhere; a wrong receipt goes through the developer runbook (FR-022).
- The fund-ledger note is hidden (the fund is F9).
- The serial no. is shown only after saving (the system assigns it).
- A strip shows Net payable (year fee), Paid so far, Pending and Due date (`dueDate` from the balance).
- Save opens the confirmation dialog (FR-008); Confirm saves and opens the print view; there is no DUPLICATE mark.

## First tasks (checks before building on them)

0. **Decisions, before anything depends on them** (in the task list the four structural rows are written at the start of the migration task, the rest in the last task; a row is "owner answer 2026-10-28, to be confirmed in review" or "proposed, awaiting owner confirmation", and none blocks a task): write the decision-log rows: "Paid up to" not on the receipt, print or register, only on the profile and Students list; no DUPLICATE mark and no print record (agreement section 8); a voided receipt's status through the `void_receipt` function as the one manual data change, with `psql` availability on the school PC handed to F11; a voided receipt's number is reusable; receipts and marks are two tables; the Students-list read of the two fees views (Complexity Tracking); money fields unmasked in the Fees audit area; F2's brought-forward visibility rules (FR-009, FR-010) end where receipts exist; the F2 payment order is kept. Add notes to agreement sections 4.1, 4.4, 8 and 11.
1. **Start from a green build**: run `scripts/verify` once on the branch before any change.
2. **Spikes**: (a) a per-student advisory lock (`pg_advisory_xact_lock`) serialises two simultaneous saves on PostgreSQL 15 with the test database; (b) the SQL "Paid up to" expression (`round` on `numeric`) equals the Java `PaidUpTo` for a table of cases including `.5` boundaries; (c) the views and the Students list meet the 2 s goal with 10,000 students and 10,000 receipts, with one join per row (not one subquery per column), checked with `EXPLAIN`; (d) the register page, a date-range filter and the export of 10,000 receipts meet 2 s and 30 s. If (c) or (d) fails, the fallback is a summary table owned by `fees`, written by triggers in the same transaction, and the spec's FR-016 is re-opened with the owner first; decide then and record it in research R2.
3. **Small checks**: confirm `SensitiveMasker.MASKED` holds none of `total`, `registrationPaid`, `feePaid`, `chequeNo`, `bankName`; confirm the table framework's date filter can express a from-to range on `receiptDate`; confirm the F0 stale-save dialog can show different text or add `balance-changed-dialog`; confirm `flyway.clean()` drops the immutability triggers (tests reset the schema that way); confirm Spring maps the receipt-number trigger's `23505` error (with the constraint name) to the `KNOWN` entry; confirm whether `FeeAccountSync` changes the account's `updated_at` when nothing changed (the balance token uses it); confirm the PostgreSQL version in use and whether `psql` is on the school PC (a check for F11, the runbook needs it).
4. **Money rule for the frontend**: amounts are formatted to two decimals with Indian digit grouping; the live total on the form and the balance after come from a server preview call, not from the browser (R3 of F2). The confirmation dialog repeats server-returned values.

## Task budget (about 30)

The task list is `tasks.md` (32 tasks); the table below is the plan's earlier estimate of 29, kept for the shape. One task covers one group of files, with its tests in the same task.

| Group | Tasks | Notes |
|---|---|---|
| Decisions, checks and spikes | 4 | first the decisions task (decision-log rows, agreement notes); green build and spikes (a) and (b); performance spike (c) and (d); module plumbing and `ModuleDependencyTest` |
| Database | 2 | `V8` with triggers, views, `void_receipt` and its tests; entities and repositories |
| Pure rules | 2 | `PaidUpTo` and `AmountInWords` with reference-case tables; validation rules and codes |
| Backend | 7 | balance service and token; create current-year; create previous-years; mark not cleared; audit and FR-025 guard; context and profile endpoints; Students list columns |
| Register and export | 2 | register table and filters; export with day-wise totals (shared exporter change) |
| Frontend | 8 | service, routes and Finance tab; receipt form; confirmation and balance-changed dialogs; receipt view and mark dialog; print component; register screen; profile Fees block; Students list columns and filter |
| End-to-end | 2 | enter, confirm, print, stale pop-up; mark not cleared, immutability, register and export |
| Docs and final | 2 | runbook with its test and contract sync; FR coverage, verify, `quickstart-results.md` (SC-001 timing) |

That is 29, with one task of room. If it grows, split nothing; defer only the on-screen day-wise totals panel (the export keeps the totals, FR-020). Everything else is needed by a MUST requirement and is not deferred: the Fees status filter (FR-015) and "balance before and after" (FR-014) stay in the plan.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| The developer void `void_receipt` is run by hand through `psql`. Constitution V prohibits manual changes to a database. | A wrong receipt cannot be fixed in the application (FR-009, the owner's decision), and the receipt must stay on record (II). The function is the narrowest manual change: it inserts one mark and one audit row in one transaction, refuses an empty reason or name, is never called by the application, and the runbook requires a backup first. | (a) An admin screen to void would make a receipt cancellable from the application, which the owner refused. (b) Editing rows by hand would break II and leave no audit row. Recorded in the decision log (first task); approved by the owner on 2026-10-30. |
| The Students list (module `students`) reads two views owned by `fees`, `v_fee_position` and `v_earlier_pending`, with read-only subqueries for the Paid, Pending, Earlier years pending, Paid up to and Fee status columns. It already reads `fee_accounts` for Total fee (F2). Constitution VII allows one read-only list-column exception; this extends it from one table to one table and two views. | The list is server-paged with sort, filter and export on every column (F0 FR-029 to FR-033), so the figures must be computed in SQL with the page. Reading views instead of the receipt tables keeps the receipt rules (valid, not marked) in one SQL place owned by `fees`, and keeps `students` from seeing `receipts`, `receipt_marks` or `fee_brought_forward`. `ModuleDependencyTest`'s table-name scan (F2 T007) gets `v_fee_position` and `v_earlier_pending` as the only new allowed reads. Approved by the owner on 2026-10-30 (decision-log row). | (a) Reading `receipts` and `receipt_marks` directly spreads the "valid receipt" rule into `students`. (b) A balance column on `student_enrollments` written by `fees` is a stored running total, which FR-016 forbids. (c) Loading the page first and adding balances afterwards breaks sorting, filtering and the export on Paid and Pending. |

## Constitution Check after design

Re-checked against [data-model.md](data-model.md), [research.md](research.md) and [contracts/openapi.yaml](contracts/openapi.yaml): Principles I to VI and VIII pass as above. Principle VII passes with the one extension in Complexity Tracking. No other exception. The mark and the void are reversal entries (Principle II); the audit entry of each is in the same transaction (Principle III); the only new migration is V8 (Principle V).

## Open questions for the owner

Q1 to Q10 were answered by the owner on 2026-10-28 and are built into the plan and the spec (Q1 receipt number stays unique, typed by the admin; Q2 separate mark record, cancel link removed everywhere; Q3 developer void with the number reusable; Q4 "Paid up to" only on the profile and list, the last paid day, `HALF_UP`; Q5 computed balances; Q6 and Q7 balances and columns as planned; Q8 no DUPLICATE mark, no print record; Q9 school name only for now; Q10 receipts dated before the year start are allowed, only future dates are refused). One question is still open and is not assumed answered.

1. **Q11 Very small payments.** A payment so small that it rounds to 0 days paid shows the start date as "Paid up to" (FR-012). Ask whether that is acceptable or whether "Paid up to" should show "Not yet" until a whole day is covered. The default is recorded in `docs/decision-log.md` (2026-10-28) as "proposed, awaiting owner confirmation"; so is the on-screen totals panel; the Students-list views and the manual void were approved by the owner on 2026-10-30. Every other default of this plan is recorded there as "owner answer 2026-10-28, to be confirmed in review".

Two consequences of the owner's answers are written down so they are not a surprise:

- **Voided numbers are free again.** After a void, the corrected receipt can use the same number; the voided receipt stays in the register with its number, so the register can show two rows with one number, one of them voided. Reports that group by receipt number (F9) must ignore voided receipts.
- **Without a print record, nothing proves a receipt was printed or how often.** The agreement's DUPLICATE rule is dropped by the owner's decision, not kept silently.
