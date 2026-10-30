# Quickstart results: 004 fee collection

Run on 2026-10-30, branch `004-fee-collection`, development PC (20 processors, 8 GB heap, Windows 11, Java 21, PostgreSQL 15). Every scenario is run by its automated test; no scenario needed a manual step.

## Automatic checks (T029, T030)

| Check | Result |
|---|---|
| `scripts/verify.sh` (2026-10-30) | exit 0 |
| Backend (`./mvnw verify`) | 666 tests, 0 failures, 0 errors, 0 skipped |
| Frontend unit tests | 274 passed (34 files) |
| Playwright | 173 passed |
| FR coverage, strict (T029) | 46 of 46 covered overall; 25 of 25 for this branch (FR-001 to FR-025) |
| Migration `V8__receipts.sql` | applied on an empty database (every IT class rebuilds the schema) and on a V7 database (`ReceiptSchemaIT`) |

Notes recorded earlier:

- The automatic daily backup is F11's. Until then `docs/runbooks/correct-receipt.md` gives the explicit `pg_dump` command to run by hand before a correction.
- `psql` on the school PC is assumed by the runbook; the check is F11's.
- T028: `CLAUDE.md` needs no change (`campusflow.school.*` has a default for the name and empty address and phone; the test-support endpoints exist only in the test profile).
- T001 starting point and T002 spike and small-check results are in `research.md` ("Starting point", R2, R3, R4, "Checks").

## Scenarios 1 to 22

| # | Covered by | Result | Observed |
|---|---|---|---|
| 1 | `ReceiptCreateIT`, `receipt-entry.spec.ts`, `receipt-flow.spec.ts` | pass | total 10,500.00, dialog repeats server values, Cancel keeps values |
| 2 | `ReceiptCreateIT`, `receipt-entry.spec.ts` | pass | 3100 refused naming the existing receipt; "03100" accepted |
| 3 | `ReceiptCreateIT`, `ReceiptMoneyInputIT`, `receipt-entry.spec.ts` | pass | future date, 0 and 0, 100.123 and -1 each refused beside the field |
| 4 | `ReceiptCreateIT`, `receipt-entry.spec.ts` | pass | Cheque fields required; Cash shows none |
| 5 | `ReceiptOverpaymentIT`, `receipt-entry.spec.ts` | pass | "Only 26500.00 is still due for the year fee." |
| 6 | `StudentFeesBalanceIT`, `StudentListFeeColumnsIT`, `fees-student-views.spec.ts` | pass | paid 10,500.00, pending 26,500.00, Paid up to 9 July |
| 7 | `PaidUpToTest`, `StudentFeesBalanceIT` | pass | 30 September (182.5 days rounded half up) |
| 8 | `StudentFeesBalanceIT`, `PaidUpToTest` | pass | "Fully paid for the year", pending 0.00 |
| 9 | `PreviousYearsReceiptIT`, `receipt-entry.spec.ts` | pass | `BROUGHT_FORWARD_PENDING`, previous-years type offered first |
| 10 | `PreviousYearsReceiptIT`, `receipt-previous-years.spec.ts` | pass | 2,000.00 saved, 1,500.00 over the 1,000.00 left refused |
| 11 | `BroughtForwardLimitIT`, `receipt-previous-years.spec.ts` | pass | 1,500.00 refused showing 2,000.00; 2,000.00 accepted |
| 12 | `ChequeNotClearedIT`, `receipt-mark.spec.ts` | pass | empty reason refused; 5,000.00 added back; action gone |
| 13 | `ChequeNotClearedIT`, `receipt-mark.spec.ts` | pass | 3301 saved; 3300 refused (`DUPLICATE_RECEIPT_NO`) |
| 14 | `ReceiptFeeStateIT`, `receipt-entry.spec.ts` | pass | Left student pays; no fee row gives no form |
| 15 | `BalanceChangedIT`, `ReceiptConcurrencyIT`, `receipt-entry.spec.ts`, `receipt-flow.spec.ts` | pass | pop-up "The balance has changed"; nothing saved until Refresh |
| 16 | `ReceiptPrintDataIT`, `receipt-print.spec.ts`, `receipt-flow.spec.ts` | pass | two prints identical; no DUPLICATE, no Paid up to; status printed |
| 17 | `ReceiptRegisterIT`, `receipt-register.spec.ts` | pass | filters, search, status words |
| 18 | `ReceiptExportIT`, `receipt-register.spec.ts` | pass | numeric cells; "Day-wise totals" sheet 2 / 10,500.00 / 5,000.00 / 15,500.00 |
| 19 | `ReceiptImmutabilityIT`, `ReceiptSchemaIT`, `ReceiptSecurityIT` | pass | no PUT, PATCH or DELETE; UPDATE, DELETE, TRUNCATE refused by trigger |
| 20 | `ReceiptAuditIT`, `ChequeNotClearedIT`, `receipt-flow-corrections.spec.ts` | pass | Create, Edit and Export entries, money in full |
| 21 | `VoidReceiptIT` | pass | mark and audit row written; number reusable; second call fails |
| 22 | `VoidReceiptIT` | pass | runbook has the six required sections |

## Success criteria

| SC | Covered by | Result |
|---|---|---|
| SC-001 under 1 minute | `receipt-flow.spec.ts` | pass: saved after 3,261 ms and print view after 3,894 ms on the first run (578 ms and 1,011 ms in `verify.sh`). A regression guard for the number of steps; it cannot show how long a person takes |
| SC-002 balances match hand figures | `FeeBalanceIT`, `PaidUpToTest`, `StudentFeesBalanceIT` | pass |
| SC-003 invalid receipts save nothing | `ReceiptCreateIT`, `ReceiptConcurrencyIT` | pass |
| SC-004 a receipt cannot change | `ReceiptSchemaIT`, `ReceiptImmutabilityIT` | pass |
| SC-005 total in words | `AmountInWordsTest` | pass |
| SC-006 export totals equal valid receipts | `ReceiptExportIT`, `receipt-register.spec.ts` | pass |
| SC-007 every change audited | `ReceiptAuditIT`, `ChequeNotClearedIT`, `ReceiptExportIT` | pass |
| SC-008 lists under 2 s | see timings | pass |
| SC-009 every FR tested | `check-fr-coverage.sh --strict` | pass, 25 of 25 |

## Timings (development PC, not the 4 GB school PC; carry to F11)

The timing tests are tagged `performance` and left out of `verify.sh`; run with `./mvnw test -Dtest='ReceiptRegisterPerformanceIT,StudentListPerformanceIT' -Dtest.groups=performance -Dtest.excludedGroups=none`.

| Test | Operation | Time | Limit |
|---|---|---|---|
| `ReceiptRegisterPerformanceIT` (10,000 receipts) | page, filters, search, sorts | 7 to 261 ms (status Valid, the slowest, 259 ms) | 2,000 ms |
| | day-wise totals of all receipts | 1,915 ms | 5,000 ms |
| | export with day-wise sheet | 2,374 ms | 30,000 ms |
| `StudentListPerformanceIT` (Students list with fee columns) | sort by Paid, Pending, Paid up to | 173, 206, 240 ms | 1,500 ms |
| | filter by Fee status | 679 and 625 ms | 1,500 ms |
| | export of 10,000 students | 25,395 ms | 30,000 ms |

**Watch item:** the Students export (25.4 s of a 30 s limit, 27.1 s in an earlier run) is the thinnest margin, and the 4 GB school PC will be slower. F11 should measure it there. The spike in `research.md` R2 and R10 compares the per-column and join forms.
