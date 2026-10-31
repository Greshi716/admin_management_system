Result: APPROVED

# Self-review: 004-fee-collection (F3 Fee collection and receipts)

- Branch `004-fee-collection`, HEAD `1a4acf7`, 39 commits, `git diff main...HEAD`: 166 files, +14,979 / -104. Working tree clean.
- Read: `specs/004-fee-collection/` (spec, plan, tasks, data-model, research, quickstart, quickstart-results, contracts/openapi.yaml), `.specify/memory/constitution.md` 1.1.0, `CLAUDE.md`, `docs/decision-log.md`, every report in `docs/reports/004-fee-collection/` (review-plan, three diff reviews, fixes.md, test-plan, test-implementation, test-results), the agreement diff, the runbook, and the backend and frontend code of the diff.
- Reviewer: self-review agent (did not write this code), 2026-10-30. I did not re-run `scripts/verify.sh`. I relied on `test-results.md` (Result: PASS on `1a4acf7`: backend 691/691, Vitest 274/274, Playwright 173/173, strict FR coverage 25 of 25).

Counts: 0 blocking, 3 should fix, 10 minor.

## Verdict

The feature does what the spec says, and the constitution and never-do rules hold:
- **Money**: `BigDecimal` and `NUMERIC(12,2)` everywhere. `HALF_UP` happens in one place in Java (`PaidUpTo`) and has one matching SQL twin (`StudentTable.paidUpTo`). With amounts of at most 12 digits, the SQL division cannot round differently from Java at a .5 boundary.
- **Immutability**: `@Immutable` entities. Append-only triggers on both tables for UPDATE, DELETE and TRUNCATE. No endpoint edits, cancels or deletes a receipt.
- **Audit**: each receipt and each mark writes its audit entry in the same transaction (`AuditService.record` is MANDATORY). The void writes its entry inside `void_receipt`.
- **Migrations**: the only migration change is the new `V8`.
- **Secrets**: nothing tracked matches `.env`, credentials, `backups/` or `real-data/`.
- **Owner approvals**: the two constitution exceptions are approved in the decision log (2026-10-30).

None of the findings blocks the push. The three SHOULD FIX items are a message format against the spec wording, one undocumented display default, and one owner confirmation still open.

## Owner decisions before the push (answer to the caller's question)

**Nothing has to be decided by the owner before the push.** Two items need the owner before merge to `main` or go-live, not before the push:

1. **Q11** (a payment that rounds to 0 days shows the start date). The rule is written into spec FR-012 itself ("When some year fee is paid but it rounds to less than one day, the date is the start date"), so the code follows the spec. The decision-log row is still "proposed". See R3.
2. **Test-plan Q5** (Fee status when only previous-years receipts are paid). The code chooses "Partly paid" and says "default until the owner answers" in a comment. It is recorded nowhere. Record it as proposed. See R2.

The other test-plan questions do not need the owner:

| Q | Assessment |
|---|---|
| Q1 | Words wording, e.g. "Fifty Paise Only", "One Paisa Only", "One Rupee Only". Wording only; the FR-011 example is met. Optional owner look at a printed sample. |
| Q2 | 36,499.50 of 36,500.00 shows the last day of the year as a date, not "Fully paid". This matches FR-012 ("Fully paid" only when the fee is fully paid). |
| Q3 | Cash with cheque fields is refused (422). This is a technical choice. |
| Q4 | Receipt-number characters: any text, 1 to 20 characters. Covered by the spec Assumptions. |
| Q6, Q7 | Profile Paid and Pending include earlier years; the list's do not. As written in FR-014 and FR-015. |
| Q8 | Day totals include previous-years receipts and exclude marked ones. As FR-020 says. |
| Q9 | An omitted amount means 0.00. Technical. |
| Q10 | A voided previous-years receipt returns its amount to the earlier pending. This follows from "not counted as paid". |
| Q12, Q13, Q14 | Technical. |
| Q15, Q16 | Process. |
| Q17 | Covered by the 2026-10-28 receipt-date row. |

The day-wise panel row needs no decision: the panel was built, so the deferral option is moot (R10).

## Checks

1. **Tasks**: T001 to T032 are all `[X]`. No TODO, FIXME, `console.*`, `System.out` or commented-out code in the diff. No `float` or `double` money in main or test code. The F2 `ExcelExporter` double check is unchanged and existing.
2. **Test findings**: T1 is fixed in `management/src/main/java/com/admin/management/fees/Money.java:31-33` (a negative scale is refused), with `MoneyTest` and `ReceiptEdgeCasesIT`. No test was weakened:
   - The removed F2 "not containsString(receipt/paid/pending)" assertions in `BroughtForwardIT`, `StudentFeesIT`, `StudentListIT` and `FeeListColumnsIT` were replaced by positive ones, as FR-014 and FR-015 require.
   - `ModuleDependencyTest` exempts only `fees/FeeTestSupportController.java` and gained a new guard test.
   - T2 to T4 needed no fix. T5 (manual scenarios) is handled under R9.
3. **FRs against the spec wording**: all 25 are implemented as written, apart from R1 (message format), R2 (undocumented status default), R4 (race-path duplicate message) and R7 (contract drift). Checked in the code:
   - Order rule and overpayment limits: `ReceiptRules`.
   - Left student and no class record: `FeeCollectionService.Current.state`.
   - Number reuse only after a void: V8 trigger and `ReceiptService.rejectUsedNumber`.
   - Paid up to: `PaidUpTo`, `StudentTable.paidUpTo`.
   - FR-025 limit under the student lock: `BroughtForwardService`.
   - Mark audit with paid-up-to before and after: `ReceiptMarkService`.
   - Day totals of valid receipts only: `DayTotals`.
   - Print header: `SchoolProperties`.
4. **Constitution and never-do list**:
   - I: holds.
   - II: holds, apart from the test-only bypass in R8.
   - III: same-transaction audit.
   - IV: all foreign keys are RESTRICT.
   - V: V8 only; the manual void is approved.
   - VI: synthetic fixtures; the real school name is public configuration.
   - VII: views only, approved.
   - VIII: 25 of 25 FRs covered.
5. **Security**:
   - Every new endpoint is under `/api/**`, which requires authentication. CSRF is global and tested in `ReceiptSecurityIT`.
   - Validation is on the server.
   - 409 and 422 bodies carry no stack trace or SQL (tested).
   - SQL built as strings holds only constants, ids and ISO dates (`StudentTable.dateCase`, `ReceiptTable`). Values are bound.
6. **Boundaries and naming**: `fees` uses only `students.api` and `masterdata.api`. `students` reads only `fee_accounts` and the two views. Some extra classes are not in the plan's list (`FeeCollectionService`, `ReceiptRules`, `ReceiptSummaryReader`, `ReceiptStatus`, input and dto records). They are reasonable splits, and there is no dead code apart from R6.
7. **Docs**: the agreement sections 4.1, 4.4, 8 and 11 are updated. The runbook has all six sections. The decision-log rows exist. `CLAUDE.md` needs no change. There is stale text in plan.md and quickstart-results.md (R9, R10).

## Should fix

**R1. SHOULD FIX: refusal messages show amounts without digit grouping, unlike the spec wording and every screen**
- Location: `management/src/main/java/com/admin/management/fees/ReceiptRules.java:61-63` (BROUGHT_FORWARD_PENDING), `:216-218` (OVERPAYMENT); `management/src/main/java/com/admin/management/fees/BroughtForwardService.java:61-63` (BELOW_PREVIOUS_YEARS_PAID).
- Problem: the messages say "Only 26500.00 is still due" and "already total 2000.00", and the frontend shows them unchanged. Spec US3 scenario 4 says "refused with a message showing 3,000.00", and FR-023 gives "10,500.00" as the display form. The screens format money with `en-IN` grouping (`shared/ui/money.ts`), so the same amount looks different in a message and in the field next to it. The tests (`ReceiptOverpaymentIT:72`, `BroughtForwardLimitIT:98`) pin the ungrouped text.
- Fix: format the amount in these three messages with Indian grouping and two decimals (one helper in `Money`), and update the two assertions.

**R2. SHOULD FIX: Fee status default "previous-years payments count as paid" is not recorded anywhere**
- Location: `management/src/main/java/com/admin/management/students/StudentTable.java:216-227`. The comment says "default until the owner answers". Test-plan Q5.
- Problem: FR-015 says "Partly paid when something is paid and something is pending". The code counts a previous-years payment as "something paid", so a student who paid only earlier years shows "Partly paid", not "Unpaid". This is a reading of the spec that the owner has not confirmed. Unlike every other default of this plan, it has no decision-log row (tasks T032 requires every default to be recorded). CLAUDE.md says a rule not in the agreement must be flagged, not invented.
- Fix: add a decision-log row "Fee status counts previous-years receipts as paid (F3, FR-015). Status: proposed, awaiting owner confirmation".

**R3. SHOULD FIX: Q11 still proposed (owner confirmation outstanding)**
- Location: `docs/decision-log.md` (2026-10-28 row "A year-fee payment so small that it rounds to 0 days..."); `management/src/main/java/com/admin/management/fees/PaidUpTo.java:52`; `StudentTable.java:200` (`greatest(...,1)`).
- Problem: the built behaviour matches spec FR-012 and the spec Edge Cases, but the spec itself says "to be confirmed by the owner". Nothing is wrong in the code. This is an open owner item.
- Fix: ask the owner before merge to `main`. If they choose "Not yet", change `PaidUpTo` and the SQL twin together and update `PaidUpToTest` and `PaidUpToRoundingTest`.

## Minor

**R4. MINOR: the race path of a duplicate receipt number does not name the existing receipt.** `ReceiptService.rejectUsedNumber` (`ReceiptService.java:120-131`) runs before the trigger takes the number lock. When two saves of one new number race, the loser is refused by the V8 trigger and gets the generic `ApiExceptionHandler` text "This receipt number is already used by another receipt." without the date and serial no. that FR-001 asks for. Nothing wrong is saved. Fix: take `pg_advisory_xact_lock(hashtextextended('receipt-no:'||upper(no),0))` before `rejectUsedNumber`, so the pre-check always sees the winner.

**R5. MINOR: the "exponent notation" fix is partial, and F2 inputs differ.** `Money.problem` refuses only a negative scale. `1.5E1` (scale 0) and `1000E-2` are still accepted as 15.00 and 10.00. They are exact and not rounded, so FR-023 holds, but the fix message in fixes.md overstates it. `BroughtForwardService.validate` (`:120-127`) and `FeeStructureService` (`:262-268`) still accept `1e3`. fixes.md says this is "left for the owner", but it is a developer consistency question, not an owner decision. Fix: have the two F2 validators call `Money.problem`, and correct the fixes.md wording.

**R6. MINOR: FR-025 "on the screen" relies on the server message only, and `previousYearsPaid` is unused.** `FeeBalance.previousYearsPaid` is sent to the browser (`receipts.service.ts:36`) but no component reads it. The student form shows the server's 422 beside the amount (`student-form-edit.spec.ts`), which T020 accepts. Fix: either use the figure for an inline hint on the brought-forward field, or drop it from the payload and the type.

**R7. MINOR: the contract is not fully synced to the build (T028).** `specs/004-fee-collection/contracts/openapi.yaml`:
- does not list the 409 code `NO_EARLIER_PENDING` (`ReceiptRules.java:105`);
- names `FUTURE_DATE` (`:142`), but the server returns a plain field error with no code;
- lacks `previousYearsPaid` in the balance schema.

Fix: update the contract.

**R8. MINOR: a test-only endpoint in the production jar hard-deletes receipts with the triggers switched off.** `management/src/main/java/com/admin/management/fees/FeeTestSupportController.java:55-61` (`SET LOCAL session_replication_role = replica`, then `DELETE FROM receipts`). It is guarded by `@Profile("test")` and `TestProfileGuard` (database name ends `_test`), which follows the F1 and F2 precedent (F1 review M6). It is the first code that bypasses the append-only rule, and no decision-log row names it. Fix: add one line to the decision-log open item on test-support controllers, or move them to `src/test` in a later feature.

**R9. MINOR: the report and results files disagree about manual checks, and the counts are stale.**
- `specs/004-fee-collection/quickstart-results.md` says "no scenario needed a manual step" and backend "666 tests". `test-results.md` T5 lists scenarios 15, 16, 19, 21 and 22 as needing a human, and the current backend count is 691.
- My assessment: scenarios 15, 19, 21 and 22 are covered by automated tests (`BalanceChangedIT`, `receipt-flow.spec.ts`, `ReceiptSchemaIT`, `ReceiptImmutabilityIT`, `VoidReceiptIT`).
- Only scenario 16 on real paper (A4 fit and printer margins) cannot be automated. That is an owner check before go-live, not before the push.

Fix: refresh the counts and add the paper-print note to quickstart-results.md.

**R10. MINOR: decision-log and plan wording is stale.**
- `specs/004-fee-collection/plan.md:173` and `:184` still say the views exception and the manual void are "proposed". The owner approved both on 2026-10-30.
- The decision-log row on the day-wise panel is "proposed", but its only content (it "may be deferred") is moot because the panel was built.
- The seven "owner answer 2026-10-28, to be confirmed in review" rows match the code. I checked each: two tables and marks; no DUPLICATE (FR-018 tests); Paid up to only on the profile and list; number reuse after a void (V8 trigger); unmasked Fees money; hiding ends; payment order with FR-025; receipt date rule.
- One part of the masking row is unsure (see Unsure points): it credits "cheque no. and bank name in full" to the owner answer, while spec FR-021 speaks only of money fields. The cheque part comes from research R7.

Fix: update plan.md, mark the panel row "built in F3, no decision needed", and change the seven rows to "confirmed in F3 review 2026-10-30" (ask the owner about cheque no. and bank name in the masking row).

**R11. MINOR: small style and token items.**
- `frontend/src/app/features/finance/receipt-form/receipt-form.component.html:17` uses an inline `style="min-height: 44px"`. Use `.link-target` or `--spacing-control`, as round-1 M5 did elsewhere.
- `FeeBalance.java:84` uses an inline `java.math.RoundingMode`.
- `FeeCollectionService.java:113` has a 151-character Javadoc line (round-3 M1, still open).
- `StudentLookupService.summary` has no `@Transactional(readOnly = true)`, unlike its neighbours.

**R12. MINOR: commit `a1833f4` "fix S2: ..." carries no FR id.** CLAUDE.md asks for the requirement ID in every task commit. Process only; history is not rewritten (no force-push). Fix: none needed now. Name the FR in future fix commits.

**R13. MINOR (carried): the duplicate `## Open items` section in `docs/decision-log.md`.** It was already on `main`, and the branch edits both copies identically (round-1 M9, deferred in fixes.md). Fix: merge the two sections in a later docs change.

## Unsure points

- **Cheque no. and bank name unmasked in the audit.** Constitution VI requires sensitive fields to be masked in the audit log and lists "bank" data among real data. Research R7 argues that `bankName` is not `bankAccount` and that a cheque number is not an account number, so they are not sensitive. That argument is reasonable, but it is the developer's judgement, while the decision-log row presents it as an owner answer. I cannot tell whether the owner agreed to the cheque part. Confirm it when the R10 rows are confirmed.
- **A fee account whose amount becomes "not set" after receipts.** I did not check whether F2 can set an account's amount to null after receipts exist. If it can, `FeeBalance.of` and `v_fee_position` would show the whole paid amount as "Excess paid". I found no test for this and no spec rule. Not reported as a defect.
- I did not run `verify.sh` or the `performance`-tagged tests myself. The timings in `quickstart-results.md` come from the development PC; F11 checks the 4 GB target.
