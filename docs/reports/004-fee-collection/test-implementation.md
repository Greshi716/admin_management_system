Result: PARTIAL

# Test implementation: 004-fee-collection

Most plan rows were already covered by the tests written during implementation (about 20 backend classes in `management/src/test/java/com/admin/management/fees/`, 7 Playwright specs `receipt-*.spec.ts` and `fees-*.spec.ts`, plus `AmountInWordsTest` and `PaidUpToTest`). Only the gaps were added. No production code was changed and no existing test was weakened. Nothing is committed.

## 1. Cases implemented (new files)

`management/src/test/java/com/admin/management/fees/ReceiptEdgeCasesIT.java` (17 tests, all tagged with `@Covers`):

| Plan row | Method |
|---|---|
| FR002-B3, XC-DATE1 (school date at 01:30 IST) | todayIsTheSchoolDateJustAfterMidnightInIndia |
| XC-DATE1 (23:59 IST) | todayIsStillYesterdayJustBeforeMidnightInIndia |
| FR002-F (invalid and non-ISO date; asserts 4xx, not exactly 422) | aDateThatIsNotARealDateOrNotInIsoFormIsRefusedAndNothingIsSaved |
| FR006-B, FR006-B2 (future and past cheque date, bank "X", leading-zero cheque no.) | aChequeDatedInTheFutureOrLongAgoAndAOneLetterBankAreAccepted |
| FR006-B2 (blank cheque no.) | aBlankChequeNumberIsRefusedBesideTheChequeNumber |
| FR006-F (UPI, "cash", empty; asserts 4xx) | aPaymentModeThatIsNotExactlyCashOrChequeIsRefusedAndNothingIsSaved |
| FR001-B2 (empty number) | anEmptyReceiptNumberIsRefusedBesideTheField |
| FR023-F (100.005, "abc", -0.01, 100.123) | anAmountThatIsTextOrHasHalfAPaisaIsRefusedAndNothingIsSaved |
| FR023-F ("1e3" as text) | anAmountSentAsExponentTextIsRefusedAndNothingIsSaved (FAILS, see section 4) |
| FR023-B (100.12 and 0.01 exact, JsonMoney) | anAmountWithTwoDecimalsIsStoredExactlyAndShownWithTwoDecimals |
| FR013-B (1-character reason) | aOneCharacterReasonIsAccepted |
| EC-11, FR012-X2 (82.5 days to 83, 22-06; pending 18,750.00 to 28,750.00) | markingAnEarlierChequeAfterALaterCashReceiptMovesPaidUpToToTheRemainingAmount |
| FR025-B (2,999.99 refused, 3,000.00 accepted) | broughtForwardCannotGoOneNinetyNinePaiseBelowThePreviousYearsPaidTotalButCanEqualIt |
| FR019-B3 (empty result 200) | aRegisterSearchWithNoMatchIsAnEmptyPageNotAnError |
| FR019-F2 (status "BAD" gives no 5xx) | aStatusFilterThatIsNotAStatusNeverCausesAServerError |
| FR020-B2 (empty export: header only, "Day-wise totals" sheet present and empty) | anExportWithNoRowsHasTheHeadersOnTheMainSheetAndAnEmptyDayWiseSheet |
| XC-GU1 (Gujarati bank name and reason stored unchanged, checked in the database) | gujaratiBankNameAndReasonAreStoredUnchanged |

`management/src/test/java/com/admin/management/fees/PaidUpToRoundingTest.java` (4 tests, FR-012, FR-024): FR012-B (149.99 gives 1 day, 150.00 gives 2 days, 150.01), XC-MONEY2 (2.5 days gives 3, 3.5 gives 4, 0.5 gives 1), scale independence.

Run result of the two new classes: 21 tests, 20 pass, 1 fails (the "1e3" case).

## 2. Cases already covered (not duplicated)

- FR-001: ReceiptCreateIT (normal, 20 and 21 characters, trim, leading zero, duplicate with date and serial, serial increases), ReceiptSchemaIT (case-insensitive, not-cleared keeps number, void frees number), ReceiptConcurrencyIT (same number at once), VoidReceiptIT (reuse after void).
- FR-002: ReceiptCreateIT (future refused, before-year-start accepted, missing date); e2e `receipt-entry.spec.ts`.
- FR-003/FR-023: ReceiptCreateIT, ReceiptMoneyInputIT (3-decimal, negative, over ten digits, both zero), ReceiptFeeStateIT (no registration fee), ReceiptOverpaymentIT.
- FR-004, FR-005, FR-007, EC-01 to EC-04: PreviousYearsReceiptIT, ReceiptOverpaymentIT, ReceiptFeeStateIT, FeeBalanceIT; e2e `receipt-previous-years.spec.ts`.
- FR-005 concurrency and EC-05, XC-LOCK1/3: ReceiptConcurrencyIT, BalanceChangedIT, ChequeNotClearedIT.
- FR-006 cheque fields, FR-010 stored fields: ReceiptCreateIT, ReceiptRepositoryIT, ReceiptPrintDataIT, ReceiptSchemaIT.
- FR-008: FeeBalanceIT (preview, validate), e2e `receipt-flow.spec.ts`, `receipt-flow-corrections.spec.ts`.
- FR-009, SC-004: ReceiptImmutabilityIT, ReceiptSchemaIT (append-only, truncate), ReceiptMigrationIT.
- FR-011: AmountInWordsTest. FR-012: PaidUpToTest, StudentFeesBalanceIT, FeeBalanceIT, ChequeNotClearedIT.
- FR-013: ChequeNotClearedIT (reason rules, cash refused, already marked, stale version, unknown serial, previous-years cheque, audit rollback, no unmark).
- FR-014, FR-015, EC-06: StudentFeesBalanceIT, StudentFeesIT, FeeListColumnsIT, FeeBalanceIT; e2e `fees-student-views.spec.ts`, `fees-list-state.spec.ts`.
- FR-016: BalanceChangedIT, FeeBalanceIT, ReceiptConcurrencyIT.
- FR-017, FR-018: ReceiptPrintDataIT; e2e `receipt-print.spec.ts`.
- FR-019: ReceiptRegisterIT, ReceiptRegisterPerformanceIT (10,000 receipts; SC-008); e2e `receipt-register.spec.ts`.
- FR-020: ReceiptExportIT (rows equal list, numeric two-decimal cells, day-wise totals, filters, one audit entry, failed export no entry).
- FR-021, SC-007: ReceiptAuditIT, ReceiptRepositoryIT, ChequeNotClearedIT.
- FR-022: VoidReceiptIT (runbook function, failures, runbook sections), ReceiptSchemaIT.
- FR-025: BroughtForwardLimitIT; e2e `fees-brought-forward.spec.ts`.
- XC-PERM1/2, XC-LOG2: ReceiptSecurityIT. XC-AUDIT1: ReceiptAuditIT.

## 3. Cases skipped, with the reason

Open questions (not asserted, per instruction): FR003-B3 (Q9, omitted registrationPaid), FR006-F3 (Q3, cash with cheque fields; the existing ReceiptCreateIT already asserts a refusal, which I did not touch), FR011-B2/B3/B4 wording (Q1; AmountInWordsTest already asserts the current wording), FR012-B 0-day cases, EC-08 (Q11), FR012-B4 (Q2), FR012-X1 date sort (Q12), FR012 audit before/after for Nothing paid or Fully paid (Q13), FR014-X1 (Q6), FR015-B Partly paid or Unpaid status when earlier years pending (Q5), FR015-X2 (Q7), FR019-B2 partial match (Q14), SC-008 timing measure (Q15), SC-001 (Q16), XC-GU3 (Q4), EC-10 interior spaces (Q4), Q8 day-totals for previous-years, Q10 void of a previous-years receipt, Q17.

Not written for other reasons:
- XC-GU2 (search by Gujarati student name), the Gujarati student-name parts of XC-GU1 and FR017-X1: the test fixtures (`FeeTestData`) fix the student name, and changing support code was out of scope for this pass. Recommend adding a name parameter to `FeeTestData.student`.
- FR012-X3 (days-in-year helper), FR012-B5 (SQL against Java on 20 cases): no public helper or SQL expression found by name without reading production code in depth; the leap-year behaviour is covered by PaidUpToTest.
- XC-DATE4 (leap day 29-02-2028), XC-DATE3, XC-LOG1 (log capture), XC-MONEY4, XC-IMM1, XC-MOD1, FR023-X1 (static scan), FR024-N/F (script tests): not added in this pass; ModuleDependencyTest and FR coverage check exist outside `fees/` and were not re-checked.
- FR020-F (unauthenticated export): not added; ReceiptSecurityIT covers receipt endpoints without a session (did not confirm export is in its list).
- Playwright: no new spec; the existing seven receipt and fees specs already carry the FR ids in titles. The e2e-only rows (FR017-X1 Gujarati print, XC-DATE2 all screens, XC-PERM3, FR016-F2 two windows) were not verified one by one, so they are listed as unconfirmed.
- Vitest: the plan asks for no frontend unit tests.
- FR-024 / SC-009: run by `scripts/verify.sh`, not by a new test; not run here as instructed.
- Manual rows (FR018-X1, FR022-X1, XC-DATA1, SC-001 timing): manual by plan.

## 4. Finding: a test that currently fails

`ReceiptEdgeCasesIT.anAmountSentAsExponentTextIsRefusedAndNothingIsSaved` (plan FR023-F). Posting `"feePaid":"1e3"` (a JSON string) to `POST /api/receipts` returns 201 and saves 1000.00. The plan expects a 422 field error ("never rounded, nothing saved"). The test is kept as written and fails until the production code refuses numbers sent as exponent text, or the owner decides it is acceptable (then the plan row should change). Plain `"abc"`, 100.005, 100.123 and -0.01 are correctly refused.

## 5. Other notes

- New classes use their own synthetic data: academic years 3800 and 3801, students 958001 and up, receipt numbers "ED-n". Amounts are checked with `JsonMoney` or `BigDecimal`; no double or float.
- FR002-F, FR006-F and the FR023-F cases assert "a 4xx response and nothing saved" rather than exactly 422, because the existing tests show some invalid inputs are rejected at JSON parsing (a different 4xx). The plan says 422; tightening this needs a look at the production handler.
