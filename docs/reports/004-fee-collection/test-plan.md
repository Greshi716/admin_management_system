Result: PLAN READY

# Test plan: 004-fee-collection (F3 Fee collection and receipts)

Planned from `specs/004-fee-collection/spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, the agreement v1.1 (2026-10-24), `docs/decision-log.md` and the constitution v1.1.0. No source code was read.

## 0. Conventions and synthetic fixtures

- Synthetic data only (constitution VI): students "Test Student A..E", GR nos "T-0001...", receipt nos like 3100 or "T-77", amounts as below. Never real names, salaries or bank names (use "Test Bank").
- Money in tests is `BigDecimal` compared with `compareTo`; no `double`/`float`. API amounts are JSON numbers with at most two decimals.
- Academic year Y26 = 01-04-2026 to 31-03-2027 (365 days, includes today 30-10-2026). Leap year YL = 01-04-2027 to 31-03-2028 (366 days). Earlier year Y25 = 01-04-2025 to 31-03-2026.
- Fee set F10 (Std 10): year fee 36,500.00 (100.00 per day in Y26), registration fee 500.00. Fee set F9 (Std 9): year fee 18,250.00, no registration fee. Fee set FZ: year fee 0.00.
- Students: A and B (Std 10, F10), C (Std 9, F9), D (Std 9, F9, brought-forward 3,000.00), E (Std 10, status Left), G (no class record in Y26, brought-forward 1,000.00), H (class record, structure not finalised), J (class record, no fee row).
- Levels: U = unit, I = integration (Spring + PostgreSQL), C = API contract, E = end-to-end (Playwright), M = manual, P = performance.
- Test ID format: `FRnnn-N` normal, `-B` boundary, `-F` failure, extra rows `-X1..`. Every automated test must carry `@Covers("FR-nnn")` or the FR id in its Playwright title.

## 1. Requirement test cases

### FR-001 Receipt number (typed, unique, serial added)

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR001-N | I,C | Student A, no receipts. POST /receipts with receiptNo "3100", valid cash amounts. | 201. serialNo present (positive integer, assigned by system); receiptNo "3100". |
| FR001-N2 | I | Save receipts "T-1" then "T-2" then void/mark none. | serialNo of second is greater than first; serial nos are distinct. |
| FR001-B | I | receiptNo of exactly 20 characters ("12345678901234567890"). | 201, stored unchanged (20 chars). |
| FR001-B2 | I,C | receiptNo of 21 characters; and "   " (blank); and "" . | Each 422 with a field error on receiptNo; nothing saved. |
| FR001-B3 | I | receiptNo "  3100  " (spaces at ends) when no 3100 exists. | 201; stored as "3100" (trimmed). |
| FR001-B4 | I | "3100" exists; save "03100". | 201 (a different number; compared as typed after trimming/case). |
| FR001-F | I,C | "3100" exists (date 15-10-2026, serial 7). Save another with "3100". | 409 code DUPLICATE_RECEIPT_NO, field receiptNo; message contains 15-10-2026 and serial 7; receipt count unchanged; no audit row for the failed attempt. |
| FR001-F2 | I | "ab-1" exists; save "AB-1" and " ab-1 ". | Both 409 DUPLICATE_RECEIPT_NO (case and end-spaces ignored). |
| FR001-F3 | I | "3300" is a cheque receipt marked Cheque not cleared; save new receipt "3300". | 409 DUPLICATE_RECEIPT_NO (a not-cleared receipt keeps its number). |
| FR001-X1 | I | "3500" voided with the developer function; save corrected receipt "3500". | 201; register shows two rows numbered 3500 (one Voided by correction); new serial greater than voided serial. |
| FR001-X2 | I | Two simultaneous POSTs, same new number, different students. | Exactly one 201 and one 409 DUPLICATE_RECEIPT_NO; one row in DB. |
| FR001-X3 | E | Save with duplicate number from the form, confirm in the dialog. | Dialog closes, message beside Receipt no. naming the existing receipt; nothing saved (list count unchanged). |

### FR-002 Receipt date

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR002-N | I | receiptDate 15-10-2026 (past, in year). | 201. |
| FR002-N2 | I | receiptDate 15-03-2026 (before Y26 start). | 201 (not restricted); receipt belongs to Y26 for a current-year receipt. |
| FR002-B | I | receiptDate = today 30-10-2026 (school time). | 201. |
| FR002-B2 | I | receiptDate 31-10-2026 (tomorrow). | 422 code FUTURE_DATE on receiptDate; nothing saved. |
| FR002-B3 | I | Clock fixed at 29-10-2026 20:00 UTC (= 30-10-2026 01:30 IST); receiptDate 30-10-2026, then 31-10-2026. | 30-10 accepted (school date is Asia/Kolkata); 31-10 refused. |
| FR002-B4 | I | Receipt dated 01-04-2026 (first day of year) and a Y26-dated receipt on 31-03-2027 when clock is after it is not possible; use 01-04-2026 only. | 201. |
| FR002-F | C | receiptDate missing; "2026-02-30"; "30-10-2026" (non-ISO in API). | Each 422 on receiptDate. |
| FR002-F2 | E | Type tomorrow's date (dd-MM-yyyy) and Save. | Error beside Date; no dialog opens. |

### FR-003 Current-year amounts

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR003-N | I | A: registrationPaid 500.00, feePaid 10,000.00. | 201; total 10500.00. |
| FR003-N2 | E | Open form for Std 9 student (no registration fee). | No registration fee row. |
| FR003-B | I | registrationPaid 0.00, feePaid 0.01. | 201; total 0.01. |
| FR003-B2 | I | registrationPaid 500.00, feePaid 0.00 (only registration). | 201; total 500.00. |
| FR003-B3 | I | registrationPaid omitted, feePaid 100.00. | 201 with registration 0.00 (or 422 field error; see NEEDS-CLARIFICATION Q9). |
| FR003-F | I,C | Both amounts 0.00. | 422 with error beside the amounts; nothing saved. |
| FR003-F2 | I | Std 9 student C with registrationPaid 100.00. | 422 (registration fee on a standard that has none); nothing saved. |
| FR003-F3 | I | registrationPaid 0.00 on F10 student already paid registration 500.00, feePaid 0. | 422 (both zero). |

### FR-004 Previous-years receipt

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR004-N | I | D (BF 3,000.00): type PREVIOUS_YEARS, previousYearId Y25, feePaid 2,000.00, cash. | 201; receiptType PREVIOUS_YEARS; academic year Y25; registration 0.00; earlier pending 1,000.00; current-year balance unchanged. |
| FR004-N2 | E | Collect fee for D. | Type selector offers Previous years' pending; year list shows only years before Y26, newest first. |
| FR004-B | I | D pays exactly 3,000.00. | 201; earlier pending 0.00; canPayPreviousYears false afterwards; canPayCurrentYear true. |
| FR004-B2 | I | Earliest academic year on file chosen. | 201 (any earlier year accepted; limit unchanged). |
| FR004-F | C | Student A (no brought-forward) tries PREVIOUS_YEARS. | 4xx refusal; E2E: type not offered. Nothing saved. |
| FR004-F2 | C | previousYearId = Y26 (current), a future year, and missing. | Each 422 (not before current year / required). |
| FR004-F3 | C | PREVIOUS_YEARS with registrationPaid 100.00. | 422; nothing saved. |
| FR004-F4 | I | Previous-years receipt for D: check response/profile. | No paidUpTo on the receipt; Paid up to of the student unchanged. |

### FR-005 Overpayment and payment order

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR005-N | I | A, fee 36,500.00: pay 10,000.00 then 8,250.00. | Both 201; year fee due 18,250.00 after. |
| FR005-B | I | A pays exactly the due 36,500.00 in one receipt (plus 500.00 registration). | 201; pending 0.00. |
| FR005-B2 | I | A has paid 10,000.00; pay exactly 26,500.00 (the due). | 201; pending 0.00. |
| FR005-B3 | I | A: feePaid 26,500.01 when 26,500.00 due. | 409 OVERPAYMENT field feePaid, current.due = 26500.00; nothing saved. |
| FR005-F | I | A: registrationPaid 500.01 when 500.00 due. | 409 OVERPAYMENT field registrationPaid, due 500.00. |
| FR005-F2 | I | D (BF pending 3,000.00): CURRENT_YEAR receipt 100.00. | 409 BROUGHT_FORWARD_PENDING, current.pending 3000.00; nothing saved. |
| FR005-F3 | I | D: PREVIOUS_YEARS 3,000.01 when 3,000.00 due; and 1,500.00 after 2,000.00 paid. | 409 OVERPAYMENT field feePaid, due 3000.00 / 1000.00. |
| FR005-X1 | I | D pays 1,000.00 previous-years in Y25 label, then another 2,000.00 labelled Y24. | Both 201 (year label does not change the limit); pending 0.00. |
| FR005-X2 | I | D pays 3,000.00 then a current-year receipt succeeds; then the 3,000.00 cheque is marked not cleared. | Earlier pending 3,000.00 again; new CURRENT_YEAR receipt refused BROUGHT_FORWARD_PENDING; already-saved current-year receipts remain Valid. |
| FR005-X3 | I | Two simultaneous 20,000.00 saves for A (36,500.00 fee; together overpay), one window each. | One 201, the other refused (BALANCE_CHANGED or OVERPAYMENT); paid total never exceeds 36,500.00. |
| FR005-X4 | I | Student with revise lowering fee 36,500.00 to 30,000.00 after 33,000.00 paid. | Year fee due 0.00; any positive feePaid refused OVERPAYMENT due 0.00. |

### FR-025 Brought-forward edit limit

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR025-N | I | D has no previous-years receipts; edit BF to 2,500.00. | 200 saved (F2 audit entry as before). |
| FR025-B | I | D valid previous-years receipts total 3,000.00: edit BF to 3,000.00; and to 2,999.99. | 3,000.00 accepted; 2,999.99 refused with code BELOW_PREVIOUS_YEARS_PAID. |
| FR025-F | I,C | Same D: edit to 2,500.00. | 422, field amount, message contains "3,000.00"; BF unchanged; no audit entry for it. |
| FR025-F2 | I | D receipts 2,000.00 (valid) and 1,000.00 (Cheque not cleared); edit BF to 2,000.00; to 1,999.99. | 2,000.00 accepted; 1,999.99 refused showing 2,000.00 (marked receipt no longer counts). |
| FR025-X1 | I | Edit BF while a receipt for D is being saved (lock). | Edit waits; result consistent (either refused with the new total or accepted before the receipt, never BF below paid). |
| FR025-X2 | E | Profile BF edit shows the server message. | Message with 3,000.00 beside the amount field (screen and server both refuse). |

### FR-006 Payment mode and cheque details

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR006-N | I | CHEQUE with chequeNo "000123", chequeDate 10-10-2026, bankName "Test Bank". | 201; cheque fields stored; receipt balance counts at once (cheque counts when received). |
| FR006-N2 | E | Choose Cash. | Cheque no., date, bank fields not shown. |
| FR006-B | I | chequeDate in the future (01-12-2026) and in the past (01-01-2020). | Both 201 (accepted as typed). |
| FR006-B2 | I | chequeNo " " (blank spaces), bankName 1 char "X". | blank refused (422 chequeNo); "X" accepted. |
| FR006-F | C | Mode "UPI", "cash" (lower), "" and missing. | Each 422 on paymentMode (Cash or Cheque only). |
| FR006-F2 | I | CHEQUE with each of the three fields missing in turn. | 422 beside that field each time; nothing saved. |
| FR006-F3 | I | CASH with chequeNo supplied. | NEEDS-CLARIFICATION Q3; assert at minimum that the stored receipt has no cheque fields. |

### FR-007 Fee state, left students, no class record

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR007-N | I | E (Left) pays 1,000.00 year fee. | 201. |
| FR007-B | I | G (no class record in Y26, BF 1,000.00): PREVIOUS_YEARS 1,000.00. | 201. |
| FR007-F | I | J (no fee row) CURRENT_YEAR. | 409 FEE_NOT_SET; message "Fee not set"; nothing saved. |
| FR007-F2 | I | H (structure not finalised) CURRENT_YEAR. | 409 FEE_NOT_FINALISED. |
| FR007-F3 | I | G CURRENT_YEAR. | 409 NO_CLASS_RECORD. |
| FR007-F4 | I | No current academic year configured. | 409 NO_CURRENT_YEAR. |
| FR007-X1 | C | GET fee-collection for J, H, G. | 200 each; currentYear.state FEE_NOT_SET / FEE_NOT_FINALISED / NO_CLASS_RECORD; canPayCurrentYear false. |
| FR007-X2 | I | J and H with a brought-forward amount pay previous-years receipt. | 201 (no current-year fee needed). |

### FR-008 Confirmation dialog

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR008-N | E | Fill form for A (3100, 30-10-2026, 500.00, 10,000.00, Cash), Save. | Dialog lists student name, 3100, 30-10-2026, 500.00, 10,000.00, total 10,500.00; no receipt in list yet. |
| FR008-B | E | Press Cancel in the dialog. | Back to the form with all values kept; nothing saved. |
| FR008-B2 | C | POST preview?validate=true with valid data; then with 0/0 amounts. | Valid: 200 with total and totalWords; invalid: 422 beside field (dialog does not open); no write, no audit entry in either. |
| FR008-F | E | Confirm with duplicate number. | Refused beside number after Confirm; nothing saved. |
| FR008-F2 | E | Whole receipt invalid in itself (cheque details empty) pressing Save. | No dialog; messages beside fields. |
| FR008-X1 | E | Dialog amounts equal the server preview, not browser-added. | Total shown equals ReceiptPreview.total. |

### FR-009 No edit, cancel or delete

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR009-N | E | Open a saved receipt view, list and profile. | No Edit, Cancel or Delete button or link anywhere. |
| FR009-B | I | Snapshot all `receipts` columns of receipt 3300, mark it not cleared, re-read. | Every column identical; a new `receipt_marks` row exists. |
| FR009-F | C | PUT, PATCH, DELETE /receipts/{serial}; POST /receipts/{serial}/cancel. | 404 or 405 each; row unchanged. |
| FR009-F2 | I | SQL UPDATE, DELETE and TRUNCATE on `receipts` and on `receipt_marks`. | Each rejected with "append-only" error; rows unchanged. |
| FR009-X1 | I | Insert a second mark for an already marked receipt. | Refused (unique); mark count 1. |
| FR009-X2 | I | Main sources scanned for any update/delete call on receipts. | None (static check). |

### FR-010 Stored receipt fields

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR010-N | I | Save a cash receipt for A. | GET shows receiptNo, serialNo, receiptDate, academicYear "2026-27" (Y26 name), student name and GR no., standard "Std 10", division name, mode, amounts, total, totalWords, receiptType, created by signed-in admin, created at (timestamp). No paidUpTo field. |
| FR010-B | I | After saving, rename student A and move A to another division. | Receipt still shows the original name, GR no., standard and division names (snapshot). |
| FR010-B2 | I | Previous-years receipt for student with a current-year record (Std 9/Div X); and for student with no class record (G). | Std/Div = shown record's values; for G both null/empty. |
| FR010-F | I | Anonymous POST /receipts. | 401; nothing saved. |
| FR010-X1 | I | Previous-years receipt academic year. | Equals the chosen earlier year (Y25), not Y26. |
| FR010-X2 | I | Current-year receipt dated 15-03-2026. | academic year is current year Y26. |

### FR-011 Total in words (Indian system)

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR011-N | U | 10,500.00 | "Ten Thousand Five Hundred Rupees Only" |
| FR011-N2 | U | 1,25,000.50 | "One Lakh Twenty-Five Thousand Rupees and Fifty Paise Only" (exact text from spec) |
| FR011-B | U | 1,00,000.00 | "One Lakh Rupees Only" |
| FR011-B2 | U | 0.50 | Words for fifty paise with no rupees part; exact wording NEEDS-CLARIFICATION Q1. Must contain "Fifty Paise" and not "Zero Rupees and" unless clarified. |
| FR011-B3 | U | 12,34,56,789.05 | "Twelve Crore Thirty-Four Lakh Fifty-Six Thousand Seven Hundred Eighty-Nine Rupees and Five Paise Only" (hyphen/and style per spec example; Q1). |
| FR011-B4 | U | 0.01, 1.00, 99,999.99, 10,00,000.00, 1,00,00,000.00 | "One Paise Only" (Q1), "One Rupee Only" (Q1), "Ninety-Nine Thousand Nine Hundred Ninety-Nine Rupees and Ninety-Nine Paise Only", "Ten Lakh Rupees Only", "One Crore Rupees Only". |
| FR011-F | U | 0.00, negative -5.00, 3 decimals 1.005 | Rejected (exception/validation); never rounded to words. |
| FR011-X1 | I | Words stored on receipt, then amounts unchanged and reprinted. | totalWords on GET equals the value stored at save. |

### FR-012 Paid up to

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR012-N | U | Y26 (365 d), fee 36,500.00, paid 10,000.00. | days paid 100; Paid up to 09-07-2026. |
| FR012-N2 | U | Same, paid 18,250.00 total. | 182.5 rounds to 183; Paid up to 30-09-2026. |
| FR012-N3 | I | A pays 10,000.00 then 8,250.00 (profile read after each). | 09-07-2026 then 30-09-2026; receipts carry no paid-up-to field; remaining fees shown beside it. |
| FR012-B | U | Paid 50.00 (0.5 day), 49.99 (0.4999 day), 150.00 (1.5 d), 149.99. | 1 day: 01-04-2026; 0 days: 01-04-2026 (start date, not "Nothing paid", spec edge; Q2/Q11); 2 days: 02-04-2026; 1 day: 01-04-2026. |
| FR012-B2 | U | Paid exactly 36,500.00 (full). | state FULL, "Fully paid for the year", date 31-03-2027. |
| FR012-B3 | U | Leap year YL (366 d), fee 36,600.00 (100.00/day): paid 10,000.00; paid 36,600.00. | days 100: 09-07-2027; full: FULL, last day 31-03-2028. |
| FR012-B4 | U | Rounding at the last day: 365-day year fee 36,500.00, paid 36,499.50 (364.995 d → 365 days). | Days paid 365 but fee not fully paid; state shown per Q2 (must not exceed 31-03-2027). |
| FR012-B5 | U | Fee 1,000.00 (365 d, 2.739726.. per day): paid 1.37 (0.5 d boundary check 0.4999). | Computed with one HALF_UP rounding at scale 0 of exact quotient; SQL expression and Java PaidUpTo give identical dates on a table of 20 cases including .5 boundaries. |
| FR012-F | U | Zero fee (FZ) with no payment. | "Fully paid for the year" (no division by zero). |
| FR012-F2 | U | Only registration fee 500.00 paid, no year fee. | "Nothing paid". |
| FR012-F3 | I | Student has only a previous-years receipt. | "Nothing paid" (previous years do not enter). |
| FR012-X1 | I | Mark a cheque (5,000.00 of 15,000.00 paid) not cleared. | Paid up to recalculated from remaining 10,000.00: 09-07-2026. |
| FR012-X2 | I | Later receipts recorded after a cheque is marked. | Paid up to continues from the remaining valid total. |
| FR012-X3 | U | days in year = end - start + 1 for a year of 01-04-2026 to 31-03-2027 (365) and leap (366). | 365 and 366. |
| FR012-X4 | I | Search all receipts / register / export columns. | No paid-up-to column on any receipt, print or register. |

### FR-013 Cheque not cleared

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR013-N | I | B: cheque receipt 3300 for 5,000.00 (year fee). POST not-cleared reason "Test returned by bank", valid balanceVersion. | 200; status CHEQUE_NOT_CLEARED; pending +5,000.00 (36,500.00 again if sole receipt); Paid up to recalculated; audit row. |
| FR013-N2 | I | After marking, pay again with receipt 3301. | 201; 3300 stays listed, not counted; 3301 counts. |
| FR013-B | I | Reason of 1 character; reason of 200 characters; 201 characters. | 1 and 200 accepted; 201 refused 422. |
| FR013-F | I | Empty reason, blank-spaces reason. | 422 beside reason; no mark row; no audit row. |
| FR013-F2 | I | Mark a Cash receipt. | 409 NOT_A_CHEQUE; nothing changes. |
| FR013-F3 | I | Mark the same cheque twice; mark a voided receipt. | 409 ALREADY_MARKED each; mark count 1. |
| FR013-F4 | C | Not-cleared on unknown serial. | 404. |
| FR013-F5 | E | Open a Cash receipt, an already marked receipt. | Action not offered; no undo action exists. |
| FR013-X1 | I | Mark on a previous-years cheque receipt for D. | Earlier pending increases by the amount; new current-year receipts blocked. |
| FR013-X2 | I | Stale balanceVersion on the mark request. | 409 BALANCE_CHANGED with fresh `current`; nothing marked. |
| FR013-X3 | I | Receipt number of marked receipt still used. | New receipt with same number refused (see FR001-F3). |

### FR-014 Student profile Fees block and Collect fee balance

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR014-N | I,E | A after 10,500.00 paid (500.00 registration + 10,000.00 year). | Profile shows year fee 36,500.00, registration 500.00, BF 0.00, total fee 37,000.00, paid 10,500.00, pending 26,500.00, Paid up to 09-07-2026, receipt list with status Valid, Collect fee button. |
| FR014-N2 | E | Collect fee screen, type 5,000.00. | Balance before and after shown (after from server preview, pending 21,500.00). |
| FR014-B | I | D (BF 3,000.00, fee 18,250.00, nothing paid). | total fee 21,250.00; pending 21,250.00; paid 0.00. |
| FR014-B2 | I | Student with receipts of every status. | List shows Valid, Cheque not cleared and Voided by correction rows. |
| FR014-F | I | Revise lowers year fee to 30,000.00 after 36,500.00 paid. | pending 0.00; excess paid 6,500.00 shown; no refund action. |
| FR014-F2 | C | GET fees for unknown student id. | 404. |
| FR014-X1 | I | Paid on the profile with a previous-years receipt. | Paid includes valid previous-years paid (Q6). |

### FR-015 Students list columns

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR015-N | I,E | Students list with A (10,500.00 paid). | Columns Total fee 37,000.00, Paid 10,500.00, Pending 26,500.00, Earlier years pending 0.00, Paid up to 09-07-2026, Fee status Partly paid. |
| FR015-N2 | I | Sort ascending/descending and filter on each new column; export. | Order and filter correct; Excel has the same columns as numeric cells with two decimals. |
| FR015-B | I | Fee status for: nothing paid (Unpaid), fully paid and BF 0 (Paid), fully paid current but BF pending (Partly paid per Q5), BF only 3,000.00 and nothing else due (Unpaid), fee zero (Paid). | Statuses as listed; Fee status filter returns exactly those students. |
| FR015-B2 | I | Earlier years pending = BF minus valid previous-years receipts (D: 3,000.00 less 2,000.00). | 1,000.00. |
| FR015-F | I | J (no fee), H (not finalised). | "Fee not set" / "Not finalised"; paid/pending/status null; no amount. |
| FR015-F2 | C | Filter with invalid fee status value. | 422. |
| FR015-X1 | I | Student with only a Cheque not cleared receipt. | Paid 0.00; Unpaid. |
| FR015-X2 | I | Shown record is previous year's (no current record). | Paid and Pending for that record's year (Q7). |

### FR-016 Stale balance

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR016-N | I | GET fee-collection token T; POST receipt with T. | 201; new GET returns a different token. |
| FR016-N2 | I | Balance after several receipts. | Equals independent Java recomputation from the receipts (integrity test). |
| FR016-B | I | Token changes on each of: new receipt, mark, fee revise, BF edit of that student; unchanged by another student's receipt. | Token differs after those four; equal after another student's activity. |
| FR016-F | I,C | Window 1 saves, window 2 POST with old token. | 409 BALANCE_CHANGED, `current` is fresh FeeCollection; nothing saved. |
| FR016-F2 | E | Two browser windows, same student. | Window 2 shows pop-up "balance has changed" with Refresh; no receipt saved; after Refresh balance is latest and a new Save works. |
| FR016-F3 | I | Malformed or missing balanceVersion. | 422 (missing) / 409 BALANCE_CHANGED (wrong). |
| FR016-X1 | I | Check DB: no column stores a paid or pending total. | No such column; values come from views. |

### FR-017 Print content

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR017-N | E | Print view of 3100 (A), print media emulation. | A4 page; school name from campusflow.school.name; receipt no. 3100; date 30-10-2026; name; GR no.; Std and Div; Registration fee 500.00 and Year fee 10,000.00 rows; Cash; total 10,500.00; words; signature line; no "Paid up to"; no DUPLICATE. |
| FR017-N2 | E | Cheque receipt print. | Cheque no., cheque date, bank name shown. |
| FR017-B | E | Previous-years receipt print. | One row "previous years' fee" with the earlier academic year name; no registration or year fee rows. |
| FR017-B2 | E | Address and phone not configured. | Header has school name only; no empty labels. Configured: shown. |
| FR017-F | E | Print Cheque not cleared and Voided receipts. | Page shows status "Cheque not cleared" / "Voided by correction". |
| FR017-X1 | E | Print is English only: Gujarati student name (see cross-cutting) still prints name faithfully; labels English. | Labels English; name renders in Gujarati script, no boxes. |
| FR017-X2 | E | Page size. | `@page` A4; content fits one page. |

### FR-018 Re-print identical

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR018-N | E | Print 3100 twice, compare rendered text. | Identical text/DOM. |
| FR018-B | E | Reprint from receipt list and from profile. | Same content as right after saving. |
| FR018-F | E,I | Look for DUPLICATE text; look for any print endpoint or print table. | No DUPLICATE anywhere; no endpoint; no audit row or DB row written by printing. |
| FR018-X1 | M | Decision log and agreement section 11 contain the DUPLICATE change. | Rows present (2026-10-28). |

### FR-019 Register

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR019-N | I,E | 6 receipts across 3 dates, modes, standards. POST /receipts/search no filter. | All 6 rows with receipt no., serial no., date, student, GR no., std/div, type, mode, registration/year/previous amounts, total, status. |
| FR019-N2 | I | Filter date 05-10-2026 to 10-10-2026; mode CHEQUE; standardId Std 10; status VALID; each alone and combined. | Only matching rows; combined is the intersection. |
| FR019-B | I | Date range from = to = a single day; inclusive of both ends. | Receipts on that day returned (both first and last day of range). |
| FR019-B2 | I | Search by receipt no. "3300" (exact) and part "33"; by student name (case-insensitive). | Matching rows (partial matching per Q14). |
| FR019-B3 | I | Empty result set. | 200, 0 rows. |
| FR019-F | I | Status filter VOIDED/CHEQUE_NOT_CLEARED. | Those rows listed with status. |
| FR019-F2 | C | dateTo before dateFrom; status "BAD". | 422 or empty per F0 rule (no 500). |
| FR019-X1 | E | Open receipt from the register. | View, print and mark actions available. |
| FR019-X2 | I | Two rows with one number (voided + corrected). | Both listed. |

### FR-020 Export

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR020-N | I | Export with filter date range and mode CASH. | .xlsx main sheet has the same rows as the list; money cells numeric type with format 0.00; sheet "Day-wise totals". |
| FR020-N2 | I | Day-wise sheet: 30-10-2026 two cash receipts 1,000.00 + 500.00 and one cheque 2,000.00. | Row: date, count 3, cash 1,500.00, cheque 2,000.00, total 3,500.00. |
| FR020-B | I | Not-cleared and voided receipts in range. | On the main sheet with status, excluded from count and totals. |
| FR020-B2 | I | Export with no rows. | Valid file; headers only; empty totals sheet. |
| FR020-B3 | P | Export of 10,000 receipts. | Under 30 s. |
| FR020-F | I | Export while unauthenticated. | 401, no file, no audit entry. |
| FR020-X1 | I | Totals equal to sum of valid receipts in the filter for 5 different filters. | Equal to the cent. |

### FR-021 Audit

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR021-N | I | Save receipt 3100. | One audit row, area Fees, action Create, entity Receipt#serial, new values: receiptNo, studentId, type, mode, registrationPaid 500.00, feePaid 10000.00, total 10500.00 (cheque: chequeNo, bankName) in full, not masked. |
| FR021-N2 | I | Mark cheque not cleared. | Audit action Edit, status VALID to CHEQUE_NOT_CLEARED, reason, amountAddedBack, paidUpToBefore, paidUpToAfter; in same transaction. |
| FR021-N3 | I | Export. | One audit row, area Fees, action Export, filters and row count, written after the file is produced. |
| FR021-B | I | Cash receipt vs cheque receipt entries. | Cash entry has no cheque keys; cheque entry has them in full. |
| FR021-F | I | Force the audit service to fail on receipt save and on mark. | Request fails; no receipt, no mark persisted (rollback). |
| FR021-F2 | I | Rejected receipts (duplicate, overpayment, validation). | No audit rows written. |
| FR021-X1 | I | Export fails mid-way. | Export entry only if the file was produced (per F0 independent transaction). |
| FR021-X2 | I | Money field names not in masked list. | Values like "10000.00" not "****". |

### FR-022 Developer void and runbook

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR022-N | I | `SELECT void_receipt(serial, 'TEST-1 wrong student', 'developer')`. | Mark DEVELOPER_VOID row (amount = total); audit row Fees/Edit, status VALID to VOIDED, by "developer", reason; receipt shows "Voided by correction"; not counted as paid. |
| FR022-B | I | After a void, the number is reused (FR001-X1), serial never reused. | New receipt accepted; serials distinct. |
| FR022-F | I | Void call with empty reason; with empty/blank done_by; second call on same receipt; missing serial; on a Cheque-not-cleared receipt. | Each fails with an error; no mark, no audit row added. |
| FR022-F2 | I | Search main sources and frontend for `void_receipt`. | Not found (application offers no void). |
| FR022-X1 | M | Read `docs/runbooks/correct-receipt.md`. | Contains when to use, who may do it, backup first, steps, what is recorded, check afterwards; says not to remove audit trail. |
| FR022-X2 | I | Voided receipt cannot be marked cheque not cleared. | 409 ALREADY_MARKED. |

### FR-023 Money format and rounding

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR023-N | E | 10500 stored; display. | "10,500.00" (two places everywhere; Indian grouping of digits per plan). |
| FR023-B | I,C | feePaid 100.12, 0.01, 99999999.99. | Accepted at two decimals; 12-digit limit respected (NUMERIC(12,2)); 100.123 refused. |
| FR023-F | C | feePaid 100.123, 100.005, -1, -0.01, "abc", 1e3 as text. | Each 422 field error; never rounded; nothing saved. |
| FR023-F2 | I | feePaid 10000000000.00 (over column). | 422 (not a 500). |
| FR023-X1 | U | Static scan of main and test sources for double/float on money. | None. |
| FR023-X2 | I | DB CHECKs: insert total <> reg + fee; negative amounts. | Rejected by constraint. |

### FR-024 Tests exist

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| FR024-N | I | `scripts/check-fr-coverage.sh --strict`. | FR-001 to FR-025 each have at least one referencing test; exit 0. |
| FR024-B | U | Paid up to table contains leap year, zero fee, full payment, nothing paid, rounding at last day, cheque not cleared; words table contains the FR-011 cases. | All cases present and passing (FR012-*, FR011-*). |
| FR024-F | I | Remove a tag from a test (dry run). | Strict check fails. |

## 2. Success criteria

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| SC-001 | E | Playwright flow: type receipt, confirm, open print view. | Under 60 s elapsed; print view in one more action (click); time recorded in quickstart-results.md. Human timing: M, Q16. |
| SC-002 | I | Sequences: partial; full; past-dated; cheque not cleared mid-sequence (10 sequences). | Paid, pending, Paid up to equal hand-worked figures in 100% (e.g. 10,000.00 → pending 26,500.00 after registration; 09-07-2026). |
| SC-003 | I | Invalid receipts: duplicate number, tomorrow, both zero, missing cheque no., overpayment. | 5 of 5 refused with field message; receipt count and audit count unchanged. |
| SC-004 | I,E | Attempt edit/cancel/delete by UI, API, SQL on saved receipts; mark cheque. | All refused; all columns identical after the mark. |
| SC-005 | U | Words for 0.50, 1,00,000.00, 12,34,56,789.05 plus 20 more amounts. | All correct (see FR011-*). |
| SC-006 | I | Export and day totals for 8 filters over a seeded set (including not-cleared and voided). | Sheet rows = list rows; totals = sum of valid receipts to the cent. |
| SC-007 | I | Count receipt creations, marks, exports vs audit entries; inject failures. | One audit row per success; none for failures. |
| SC-008 | P | 2,000 students and 10,000 receipts: Students list page, sort and filter by Paid and Pending; receipt register page and filter. | Each under 2 s on the dev PC (Q15 for how measured). |
| SC-009 | I | FR coverage check and full verify. | Every FR covered; all tests pass. |

## 3. Cross-cutting cases

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| XC-MONEY1 | U | Sum of 0.10 + 0.20 receipts; total equals 0.30 exactly. | `compareTo` equals; no float drift. |
| XC-MONEY2 | I | Paid up to division uses exact decimal and one HALF_UP at scale 0 (not HALF_EVEN): 2.5 days, 3.5 days, 0.5 day. | 3, 4, 1 days. |
| XC-MONEY3 | C | Response JSON money fields. | Have exactly two decimal places (e.g. 10500.00); Excel cells numeric. |
| XC-MONEY4 | I | NUMERIC(12,2) column definitions via information_schema. | All money columns numeric(12,2). |
| XC-AUDIT1 | I | Receipt insert succeeds but audit forced to fail. | Whole transaction rolled back (see FR021-F). |
| XC-AUDIT2 | I | Void function audit row in same transaction. | If the audit insert fails the mark is not persisted. |
| XC-LOCK1 | I | Two simultaneous saves, same student, both with same token. | One succeeds; the other 409 BALANCE_CHANGED; no overpayment. |
| XC-LOCK2 | I | BF edit with stale version (F2 optimistic lock) racing a receipt. | Stale edit refused with the stale-save response; no lost update. |
| XC-LOCK3 | I | Two marks of the same cheque simultaneously. | One 200, one 409; one mark row. |
| XC-DATE1 | I | Clock at 29-10-2026 19:00 UTC and 18:29 UTC (IST 00:30 and 23:59 on different days). | "today" resolved in Asia/Kolkata (see FR002-B3). |
| XC-DATE2 | E | All dates in screens (list, form, dialog, profile, print, Paid up to) shown dd-MM-yyyy, e.g. 09-07-2026. | No ISO or US format on screen; Excel date cells are real dates formatted dd-MM-yyyy. |
| XC-DATE3 | I | Created-at timestamp stored with offset; shown in IST. | 23:30 IST entry shows same calendar date. |
| XC-DATE4 | I | Receipt on 29-02-2028 (leap day) in YL. | Accepted when clock allows; Paid up to calculation across leap day uses 366 days. |
| XC-GU1 | I,E | Student name "ટેસ્ટ વિદ્યાર્થી એ" (synthetic Gujarati), bank name "ટેસ્ટ બેંક", reason "ટેસ્ટ કારણ". | Saved, shown, searchable, printed, exported to Excel without corruption or "?" characters; snapshot name identical. |
| XC-GU2 | I | Search register by Gujarati student name substring. | Matching rows returned. |
| XC-GU3 | I | receiptNo with Gujarati digits or letters ("૩૧૦૦"). | NEEDS-CLARIFICATION Q4 (allowed characters); must not cause 500. |
| XC-PERM1 | C | Every F3 endpoint (GET fee-collection, preview, POST /receipts, GET /receipts/{id}, not-cleared, search, export, day-totals, fees, brought-forward) without session. | 401 each; nothing written. |
| XC-PERM2 | C | State-changing requests signed in but without a CSRF token. | 403; nothing written. |
| XC-PERM3 | E | Open /finance routes signed out. | Redirect to sign-in. |
| XC-PERM4 | I | Signed-in admin saves; created_by. | Equals admin's user id. |
| XC-LOG1 | I | Run save, mark, export, a failing save; capture application logs. | No password, session id, CSRF token, or full request body with cheque numbers in logs; the first-run password file content not logged. |
| XC-LOG2 | I | Error responses (409/422). | Problem body has no stack trace, SQL or internal names beyond `code` and `field`. |
| XC-IMM1 | I | `flyway.clean()` then migrate (test reset). | Triggers dropped and re-created; V1 to V7 checksums unchanged; V8 applies on empty and on F2 database. |
| XC-MOD1 | I | ModuleDependencyTest. | students reaches fees only via the two views and shared interfaces; no cycles. |
| XC-DATA1 | M | Scan test fixtures and docs for real names/amounts. | Only synthetic values. |

## 4. Spec edge-case list (one row each)

| ID | Level | Given / When / Then | Expected result |
|---|---|---|---|
| EC-01 | I | J ("Fee not set") and H ("Fee structure not finalised"): current-year receipt; previous-years receipt for each with a BF amount. | Current: 409 FEE_NOT_SET / FEE_NOT_FINALISED with clear message; previous-years: 201. |
| EC-02 | I | E (Left) pays pending fees, both types. | 201 for both. |
| EC-03 | I | Go-live order: D with BF 3,000.00: current-year first; then previous-years 3,000.00; then current-year. | First refused BROUGHT_FORWARD_PENDING; second and third accepted. |
| EC-04 | I | G (no class record in Y26): current-year refused; previous-years accepted. | 409 NO_CLASS_RECORD; 201. |
| EC-05 | I | Two windows save for the same student that together overpay (20,000.00 each of 36,500.00 fee). | Second refused as stale (BALANCE_CHANGED); total paid 20,000.00. |
| EC-06 | I | Revise lowers the year fee below paid (33,000.00 paid, new fee 30,000.00). | Pending 0.00; excess paid 3,000.00 shown; no refund path. |
| EC-07 | U,I | Yearly fee zero: Paid up to. | "Fully paid for the year". |
| EC-08 | U | Year-fee payment under half a day (49.99 of 36,500.00). | Paid up to = 01-04-2026, not "Nothing paid"; flagged Q11 (not confirmed by owner). |
| EC-09 | I | Cheque dated in the future and in the past. | Both accepted. |
| EC-10 | I | Receipt number with letters ("T-77A"), spaces inside ("31 00"), leading zeros ("0031"). | Letters/leading zeros accepted as typed (zeros different from "31"); interior spaces per Q4; none cause errors. |
| EC-11 | I | Cheque marked not cleared after later receipts recorded (receipts 10,000.00 cheque, then 8,250.00 cash). | Paid up to from remaining 8,250.00 = 82.5 → 83 days → 22-06-2026; pending +10,000.00. |
| EC-12 | I,M | Wrong receipt found: UI/API offers no fix; developer void; corrected receipt same number. | No edit path; void per runbook; corrected receipt 201; register shows both. |

## 5. Questions (NEEDS-CLARIFICATION; no rule invented)

1. NEEDS-CLARIFICATION Q1: Exact wording of the total in words when there are no rupees (0.50, 0.01), singular ("One Rupee Only"), and whether "and" or hyphens appear inside the hundreds. Only the FR-011 example is fixed.
2. NEEDS-CLARIFICATION Q2: "Fully paid" is shown "when the year fee is fully paid". If the amount is below the fee but days paid rounds to the full year (e.g. 36,499.50 of 36,500.00), does the screen show "Fully paid for the year" or the last day?
3. NEEDS-CLARIFICATION Q3: Cash with cheque fields supplied by the client: ignored, refused or stored? (Spec says "not asked" only.)
4. NEEDS-CLARIFICATION Q4: Allowed characters in the receipt number (spaces inside, non-ASCII). The edge case says "the rule is in the Assumptions", which only gives 20 characters and case/end-space comparison.
5. NEEDS-CLARIFICATION Q5: Fee status when the current year is fully paid but earlier-years pending remains, or only previous-years receipts have been paid: "Partly paid" or "Unpaid"? ("Paid" in the status rule refers to the shown record only.)
6. NEEDS-CLARIFICATION Q6: Profile "Paid" includes previous-years receipts (data model) while the Students list "Paid" is current year only (spec FR-015). Confirm both are intended.
7. NEEDS-CLARIFICATION Q7: Students list "Pending" excludes earlier-years pending (separate column) while the profile Pending includes it. Confirm.
8. NEEDS-CLARIFICATION Q8: Does a not-cleared or voided receipt on a previous-years type still show in the day-wise totals count of 0? (Assumed excluded.) And do previous-years receipts count in the day-wise cash/cheque totals?
9. NEEDS-CLARIFICATION Q9: If registrationPaid is omitted (not 0) for a standard with a registration fee, is it treated as 0.00 or refused?
10. NEEDS-CLARIFICATION Q10: Developer void of a previous-years receipt: confirm the amount returns to earlier pending and blocks current-year receipts exactly as a not-cleared cheque does (spec FR-005 covers only the cheque).
11. NEEDS-CLARIFICATION Q11 (open in plan): Payment under half a day: start date vs "Not yet".
12. NEEDS-CLARIFICATION Q12: Sort and filter order for the Paid up to column when values are "Nothing paid" and "Fully paid for the year" (no date).
13. NEEDS-CLARIFICATION Q13: How the mark audit entry shows "Paid up to before and after" when the state is Nothing paid or Fully paid.
14. NEEDS-CLARIFICATION Q14: Register search: exact or partial match on receipt no.; case sensitivity of student name search.
15. NEEDS-CLARIFICATION Q15: SC-008 "under 2 seconds": API response time or screen render time, and which percentile.
16. NEEDS-CLARIFICATION Q16: SC-001 "under 1 minute" cannot be measured by automation for a person; accept the Playwright guard plus one manual timing?
17. NEEDS-CLARIFICATION Q17: Behaviour when a student's receipt would be dated outside the academic year end (a date after 31-03-2027 is blocked by "not after today" for now; a back-dated receipt for a past year is a current-year receipt of the current year). Confirm no other rule.
