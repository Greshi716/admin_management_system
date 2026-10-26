# Quickstart: Validating Fee Structure and Student Fee Accounts (F2)

Run-and-check guide. Prerequisites, JDK 21, Node and the two databases are as in
[../001-foundation/quickstart.md](../001-foundation/quickstart.md); F1 (`002-students`) must be on the branch.
Contracts: [contracts/openapi.yaml](contracts/openapi.yaml); tables: [data-model.md](data-model.md). Use only
synthetic students and amounts (constitution VI).

## 1. Automatic checks

```powershell
scripts\verify.ps1      # backend tests, frontend build and unit tests, Playwright, FR coverage (port 8080 free)
```

Expected: all pass, and the FR coverage check reports FR-001 to FR-014 (F2) covered. Migration check: Flyway
applies `V7__fees.sql` on an empty database and on an F1 database.

## 2. Setup for the manual scenarios

Sign in. Make sure an academic year covers today, standards Std 9 and Std 10 each with a division, and the seven
categories exist. Register five synthetic students: Std 10 O/S, Std 10 STAFF, Std 9 O/S, one more Std 9 O/S, and
Std 10 CEW. Open Setup, Fees and salary, Fee structure.

## 3. Scenarios

| # | Do | Expect | FR |
|---|---|---|---|
| 1 | Add rows: Std 10 (all categories) 36,000.00 with registration fee 500.00 and due date; Std 10 O/S 40,000.00 (no registration fee); Std 9 (all) 30,000.00 | Rows listed with two decimals (for example 36,000.00), status Draft tag; stored values have scale 2; audit entries in area Fees | 001, 011, 012 |
| 2 | Add a second Std 10 all-categories row; add a row with amount -1, with 100.123 and with no amount | Each refused beside the field; nothing saved | 001, 011 |
| 3 | Edit and delete a Draft row | Saved at once, delete asks to confirm; audit entries | 002, 012 |
| 4 | Press Finalise with an empty year (another year) | Refused with a message | 002 |
| 5 | Press Finalise on the Std 9 / Std 10 year and confirm | Status Finalised; Add, Edit and Delete are replaced by Revise; the five students have fees (see 6); a Finalise audit entry says how many accounts were created | 002, 003, 006, 012 |
| 6 | Open the profiles and the Students list | Std 10 O/S: 40,000.00 + registration fee 500.00 (from the blank row), total fee 40,500.00; Std 10 STAFF and CEW: 36,000.00 + 500.00; Std 9: 30,000.00, no registration fee; Total fee column in the list; no paid, pending or fee-status columns; no receipts | 005, 006, 009, 010 |
| 7 | Delete the Std 9 row through Revise, confirm | Warning first showed "5 students" (class records of the year); the Std 9 students then show "Fee not set"; audit entry with reason Revise and studentsRecalculated 5 | 004, 005, 012 |
| 8 | Press Revise, change Std 10 all-categories to 37,000.00, cancel at the warning | Nothing changes | 004 |
| 9 | Repeat 8 and confirm | Std 10 STAFF and CEW show 37,000.00; O/S unchanged; one audit entry for the row change with old and new values and the count | 004, 012 |
| 10 | Call the row API in the Finalised year without `confirm=true` (test helper) | 409 REVISE_CONFIRMATION_REQUIRED with the student count; nothing changed | 004 |
| 11 | Register a Std 10 student in the Finalised year | Fee account exists at once on the profile | 006 |
| 12 | Edit a student, set Brought forward 2,500.00 with a note, save; then change it to 1,000.50; then clear it | Fees block shows it and the total due includes it; each change has an audit entry with old and new values; a student with fee not set still shows brought forward | 007, 009, 012 |
| 13 | Open the class record dialog of a Finalised year | Standard and category are fixed with the message; division can be changed and saved | 008 |
| 14 | Send a standard or category change for that year through the API (test helper) | 409 FEE_STRUCTURE_FINALISED; nothing changed | 008 |
| 15 | Open a year with no structure or a Draft one, then a student's profile for it | "Fee structure not finalised"; standard and category can be changed as in F1 | 006, 008, 009 |
| 16 | Open the structure in two windows: edit the same row in both; then finalise in window A and, in window B (still showing Draft), edit a row | The second save is refused with the latest values shown; in the second case B reloads as Finalised and no revise warning appears for the stale edit | 013 |
| 17 | Delete all academic years covering today | Fee structure screen blocked as in F0 | 013 |
| 18 | Open the audit log, area Fees | Entries for steps 1 to 12 with old and new amounts in full; Aadhaar and religion/caste of students still masked elsewhere | 012 |
| 19 | Performance (test helper): 2,000 synthetic class records; finalise, then revise one row; and the list on 10,000 students | Each under 10 s; list under 2 s | SC-004 |
| 20 | Timing check: enter the rows for 12 standards with a due date each and finalise | Done in under 10 minutes by an admin who has used the screen once (manual stopwatch check, recorded in the results file) | SC-001 |
| 21 | Export the Students list to Excel after scenario 6; open the file | Total fee is a numeric cell with two decimals (it sums), "Fee not set" students are blank or show the text column; other amounts in other exports (audit, lists) still read correctly | 010, 011 |
| 22 | (Automated) Register a student while a finalise runs in another transaction | The student ends with a fee account and the finalise count includes or excludes it consistently, never a class record without an account in a finalised year | 006 |
| 23 | Display and scale: save a row with amount 28000 and another with 28,000.5; open the list, the profile Fees block and the Excel export; read the stored values (integration test) | Shown as 28,000.00 and 28,000.50 everywhere; the database holds scale 2 (`28000.00`); the Excel cell is numeric with format 0.00; the browser shows server totals and adds nothing | 011, 010 |

## 4. Where the checks live

- Test helpers: the existing `StudentTestSupportController` (`@Profile("test")`, `/api/test-support`) is extended to seed class records in bulk, and `frontend/e2e/support/fees.ts` sets up years, rows and students; the row API is called without `confirm` straight from the tests. Used by scenarios 10, 14, 19 and 22.
- Backend: `fees/FeeSelectionTest` (unit, every spec example), `fees/*IT.java` (rows and validation, finalise, revise confirmation and count, accounts for new class records, lock, brought-forward, audit in the same transaction), `students` tests for the changed class record rule and the list column, `ModuleDependencyTest` (extended, plus the test that lists the allowed cross-module table reads), `CodedConflictException` handler tests and the Excel exporter tests (numeric amounts).
- Frontend: unit tests for the fee structure screen and the revise dialog; Playwright `fees-*.spec.ts` (scenarios 1 to 9, 12, 13 and the layout assertions: card with year title, status tag, 44 px controls, table scrolls sideways on a phone width) and the updated students specs.
- Every test carries `@Covers("FR-xxx")` or the FR id in its title.
