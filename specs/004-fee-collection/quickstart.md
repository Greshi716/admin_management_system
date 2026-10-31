# Quickstart: Validating Fee Collection and Receipts (F3)

Run-and-check guide. Prerequisites, JDK 21, Node and the two databases are as in
[../001-foundation/quickstart.md](../001-foundation/quickstart.md); F2 (`003-fees`) must be on the branch. Contracts:
[contracts/openapi.yaml](contracts/openapi.yaml); tables: [data-model.md](data-model.md). Use only synthetic students and
amounts (constitution VI). Money: compare with `compareTo`, never `double`.

## 1. Automatic checks

```powershell
scripts\verify.ps1      # backend tests, frontend build and unit tests, Playwright, FR coverage (port 8080 free)
```

Expected: all pass, and the FR coverage check reports FR-001 to FR-025 (F3) covered. Migration check: Flyway applies
`V8__receipts.sql` on an empty database and on an F2 database.

## 2. Setup for the manual scenarios

Sign in. An academic year covers today and starts on 1 April of a 365-day year (use a synthetic year so the numbers below
hold). Standards Std 9 and Std 10, each with a division. Finalise a structure: Std 10 all categories 36,500.00 with registration
fee 500.00; Std 9 all categories 18,250.00 (no registration fee). Register four synthetic students: A and B in Std 10, C in
Std 9, D in Std 9 with a brought-forward amount of 3,000.00 (note "earlier years"). Set one student of Std 10 to Left later
(scenario 14). The school header values are in `application.properties` (`campusflow.school.*`).

## 3. Scenarios

| # | Do | Expect | FR |
|---|---|---|---|
| 1 | Student A: Collect fee, receipt no. 3100, today, registration 500.00, year fee 10,000.00, Cash, Save | Confirmation dialog shows student, 3100, date, both amounts, total 10,500.00; Cancel returns with values kept; Confirm saves; the receipt has a serial no. and the words "Ten Thousand Five Hundred Rupees Only" | 001, 003, 008, 010, 011 |
| 2 | Same screen, receipt no. 3100 again; then "03100" | 3100 refused beside the receipt no., naming the existing receipt (date, serial no.); nothing saved. "03100" is a different number and is accepted (numbers are compared as typed, after trimming and ignoring case) | 001 |
| 3 | Tomorrow's date; then both amounts 0.00; then year fee 100.123; then -1 | Each refused beside the field; nothing saved | 002, 003, 023 |
| 4 | A: Cheque with the cheque no., date and bank empty, then filled | Refused while empty; Cash shows no cheque fields | 006 |
| 5 | A: year fee 30,000.00 (more than the 26,500.00 still due) | Refused with 26,500.00 shown as still due (`OVERPAYMENT`) | 005 |
| 6 | Profile of A and the Students list after scenario 1 | Paid 10,500.00, pending 26,500.00, Paid up to 9 July (100 days paid: 10,000.00 / (36,500.00 / 365), so 1 April + 99 days; the last paid day) beside the remaining fees, receipts listed with status Valid and no paid-up-to; list columns Total fee, Paid, Pending, Earlier years pending, Paid up to, Fee status "Partly paid" | 012, 014, 015, 019 |
| 7 | A: receipt 3101, year fee 8,250.00 (18,250.00 paid in total) | Profile's Paid up to is 30 September (182.5 days rounded to 183 days paid, so 1 April + 182 days); no receipt shows a paid-up-to date | 012 |
| 8 | A: pay the rest, 18,250.00 (receipt 3102) | Profile shows "Fully paid for the year"; pending 0.00; the receipt print has no paid-up-to line | 005, 012, 017 |
| 9 | D: current-year receipt 3200 for any amount | Refused: earlier years' pending 3,000.00 must be paid first (`BROUGHT_FORWARD_PENDING`) | 005 |
| 10 | D: previous-years receipt 3201, earlier year, 2,000.00, then try 1,500.00 | First saved (pending 1,000.00, no Paid up to, printout has one row for the previous years' fee); second refused (1,000.00 still due) | 004, 005, 017 |
| 11 | D: edit the brought-forward amount to 1,500.00, then to 2,000.00 | 1,500.00 refused with 2,000.00 shown; 2,000.00 accepted | 025 |
| 12 | B: Cheque receipt 3300 for 5,000.00 (year fee); open it, Cheque not cleared, empty reason, then a reason | Empty refused; with a reason: status "Cheque not cleared", 5,000.00 added back, the profile's Paid up to recalculated, audit entry (action Edit, the reason, the student's paid-up-to before and after); the action is gone; a Cash receipt has no such action | 013, 021 |
| 13 | B: pay the 5,000.00 again with receipt 3301; then try to save another receipt numbered 3300 | 3301 saved as a new receipt; 3300 stays listed, not counted; a new receipt numbered 3300 is refused (a Cheque not cleared receipt keeps its number) | 001, 013 |
| 14 | Mark a Std 10 student Left, then collect a payment | Accepted; a student with no fee amount (Std with no row) is refused with "Fee not set"; before finalising, "not finalised" | 007 |
| 15 | Open the same student in two windows; save a receipt in one; press Save in the other | Pop-up "the balance has changed" with Refresh; nothing saved until refreshed | 016 |
| 16 | Print receipt 3100; print it again; print the Cheque not cleared receipt 3300 | Both prints of 3100 are identical (no DUPLICATE mark, nothing recorded); the page is A4 with the school name, receipt no., date, student, GR no., Std / Div, rows, mode, total, words and a signature line, no paid-up-to; 3300 prints its status "Cheque not cleared" | 017, 018 |
| 17 | Receipts list: filter by date range, Cash, Std 10, status; search 3300 | Only matching rows; not-cleared rows show their status; no paid-up-to column | 019 |
| 18 | Export the filtered list | Excel has the same rows, numeric money cells with two decimals, a "Day-wise totals" sheet that excludes the not-cleared receipt; one Export audit entry in area Fees | 020, 021 |
| 19 | Try to change or delete a receipt: no button or address; `UPDATE` and `DELETE` through the database | Refused by the database ("append-only"); all columns of 3300 identical after it was marked | 009 |
| 20 | Audit log, area Fees | Entries for each receipt, the mark and the export, money in full (not masked) | 021 |
| 21 | Developer: `SELECT void_receipt(<serial>, 'TEST-1: wrong student', 'developer')` as in the runbook; then enter a corrected receipt with the voided receipt's number | Receipt shows "Voided by correction", not counted; audit row present; the receipt row unchanged; a second call fails; the corrected receipt is accepted with the same number and the register shows both | 001, 022 |
| 22 | Read `docs/runbooks/correct-receipt.md` | Steps, who may do it, the backup, the audit row and the check afterwards are all there | 022 |

Timing check (SC-001, automated, tasks.md T026): the Playwright flow records scenario 1, including the confirmation, in under 60
seconds and opens the print view with one more action; the measured time is written to `quickstart-results.md`. It is a regression
guard for the number of steps; it cannot show how long a person takes.

Test isolation: tests that create receipts use their own synthetic students, years and receipt numbers (as `FeeTestData` does for
years), because the schema is rebuilt once per test class and a receipt cannot be deleted between tests (the triggers refuse it).
The trigger and void tests sit in their own classes.

## 4. Layout checks (Playwright)

Collect fee form, which follows the prototype with the differences listed in plan.md "Differences from the prototype" (type selector, remaining due next to each amount, live total from the server, a strip with year fee, paid, pending and due date, no cancel link, no fund ledger note, the serial no. shown only after saving; print checks emulate print media), confirmation dialog, balance-changed
pop-up, receipt view with the Mark not cleared dialog, print view (A4 media, school name from configuration, no DUPLICATE mark),
register (filters, day-wise totals panel), the profile's Fees block, the Students list columns, the Finance tab with the single
pill Fee collection. Hidden: Fee tracker, Payroll, Expenses and fund, Dashboard, Approvals, Staff, Reports.

## 5. Performance (recorded in `quickstart-results.md`)

Seed 10,000 synthetic students with fee accounts and 10,000 receipts (test-profile helper): Students list page, sort and filter by
Paid and Pending under 2 s; receipts list page and filter under 2 s; export of 10,000 receipts with the day-wise sheet under 30 s.
