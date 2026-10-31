Result: FIX FIRST

# Diff review: 004-fee-collection (whole branch, `main...HEAD`)

- Branch: `004-fee-collection`, 28 commits from `bde2ce3` to `f6131c6`, 151 files, +13,531 / -72. The working tree was clean.
- Scope: the whole branch. Checked against `specs/004-fee-collection/` (spec, plan, tasks, research, data-model, contracts), `.specify/memory/constitution.md` 1.1.0, `CLAUDE.md` (including the never-do list) and the design tokens in `frontend/src/styles.css`.
- Reviewer: diff-reviewer agent, 2026-10-30 13:10.
- Result line: the caller asked for "GO / FIX FIRST / DO NOT COMMIT". The agent definition uses "OK TO COMMIT" where the caller says "GO". The result here is FIX FIRST, which both lists share.

## Verdict

The feature is complete and well built. Every money path uses `BigDecimal` and `NUMERIC(12,2)`. Rounding is `HALF_UP` and happens in one place (`PaidUpTo`), and the SQL twin of that rule (`StudentTable.paidUpTo`) matches it. No receipt row can change: the entity is `@Immutable`, the V8 triggers refuse UPDATE, DELETE and TRUNCATE, and the API has no edit or delete path. The two reversals (cheque not cleared, developer void) are append-only marks. Each receipt and each mark writes its audit entry in the same transaction. The only migration change is the new `V8__receipts.sql`; V1 to V7 are untouched. All 25 FRs are tagged in tests. Concurrency is handled by a per-student advisory lock plus a balance token, and duplicate receipt numbers are blocked by an advisory lock on the number together with a trigger.

The branch should not be pushed yet. Two constitution exceptions are built but still marked "proposed, awaiting owner confirmation" in the decision log (B1). Many new tests assert money with `double` literals, which constitution I forbids even in tests (S1). The rest is smaller: a test guard was loosened (S2), the confirmation dialog opens for a receipt the server will refuse (S3), and the Students export is close to its time limit (S4).

Counts: 1 blocking, 4 should fix, 9 minor.

## Blocking

**B1. Constitution exceptions built before the owner approved them**
- Location: `docs/decision-log.md` (2026-10-28 rows "Developer void of a receipt", "The Students list reads two views owned by fees", plus the Q11 row); `specs/004-fee-collection/plan.md:45`, `:55`, `:174`; code in `management/src/main/resources/db/migration/V8__receipts.sql:169-198` and `management/src/main/java/com/admin/management/students/StudentTable.java:85-95, 166-241`.
- Problem: The constitution's Development Workflow says a violation must be "justified in the plan's complexity tracking and approved before implementation". The plan says the Students-list view read "needs the owner's recorded approval (a decision-log row) before the Students list task starts". Both exceptions (the manual `void_receipt` data change under Principle V, and the Principle VII read of `v_fee_position` and `v_earlier_pending`) are fully built and tested. Their decision-log rows still say **proposed, awaiting owner confirmation**. Seven other F3 rows say "owner answer ..., to be confirmed in review".
- Fix: Have the owner confirm the two Complexity Tracking rows (and decide Q11), and update their status in `docs/decision-log.md` before the push. No code change is needed unless the owner says no.

## Should fix

**S1. New tests assert money with `double` literals (constitution I: "including tests")**
- Location: 46 added lines with `jsonPath(...).value(<n>.<nn>)` money assertions (53 occurrences in the touched files). Examples: `management/src/test/java/com/admin/management/fees/StudentFeesBalanceIT.java:72-74` (19 in that file), `FeeListColumnsIT.java` (10), `PreviousYearsReceiptIT.java` (8), `ReceiptOverpaymentIT.java` (5), `ReceiptPrintDataIT.java` (3), `BroughtForwardLimitIT.java` (2), `ChequeNotClearedIT.java` (2), `StudentFeesIT.java:148-149` (2), `BalanceChangedIT.java` (1), `ReceiptCreateIT.java` (1). `main` already has 8 of these from F2, so the pattern was carried over, not invented here.
- Problem: `value(10500.00)` passes a Java `double`, and JsonPath reads the JSON number as `Double`. Money is held in a double inside the test. The assertion also cannot tell `10500` from `10500.00`, so it does not check the two-decimal rule in FR-023.
- Fix: Compare the raw JSON text or a `BigDecimal`, for example `jsonPath("$.balance.paid", BigDecimal.class)` with a configured JsonPath or `content().string(containsString("\"paid\":10500.00"))`, and also replace the 8 F2 cases.

**S2. Every `*TestSupportController` is now exempt from the module-boundary scan**
- Location: `management/src/test/java/com/admin/management/shared/ModuleDependencyTest.java:115`.
- Problem: The new check `relative.getFileName().toString().endsWith("TestSupportController.java")` lets any file with that name read or write any module's tables without the scan noticing. The exemption was added so that `fees/FeeTestSupportController` can seed `students` and `student_enrollments`. It is wider than that need.
- Fix: Exempt only the named files (for example a `Set.of("fees/FeeTestSupportController.java", ...)`), or keep the scan running on them and allow their table reads in the allow-list.

**S3. The confirmation dialog opens for a receipt the server will refuse**
- Location: `frontend/src/app/features/finance/receipt-form/receipt-form.component.ts:244-267` (`save()` sends a preview), `management/src/main/java/com/admin/management/fees/ReceiptRules.java:51-53`, `:76-78`, `:89-91` (`complete=false` skips the number, date, mode and zero-amount checks), and `:254` (when both amounts are zero, `previewBody()` is null and `input(ctx)` is sent to the preview instead).
- Problem: Several invalid receipts reach the "Confirm and save" dialog: a future date, both amounts zero, a missing receipt number, or missing cheque details. They are refused only after the admin confirms. Spec US1 scenarios 3, 5 and 6 and SC-003 say Save is "refused beside the field". The dialog is also described as the only safeguard (FR-008), so showing it for a receipt that cannot be saved (for example with total 0.00) is misleading. The e2e helper `saveAndConfirm` (`frontend/e2e/receipt-entry.spec.ts:52-59`) clicks Confirm only if the dialog appears, so it accepts both behaviours and the tests do not catch this.
- Fix: Run the `complete` checks in the Save preview (for example a `validate=true` flag on the preview call), and make the e2e assert that the dialog does not open for invalid input.

**S4. The Students export is at 85% of its limit, and the timing tests are not in `verify.sh`**
- Location: `specs/004-fee-collection/quickstart-results.md` (Timings: Students export 25,395 ms of 30,000 ms, 27.1 s in an earlier run); `ReceiptRegisterPerformanceIT` and `StudentListPerformanceIT` are tagged `performance` and excluded from verify.
- Problem: This was measured on a 20-processor development PC with an 8 GB heap. The target is a 4 GB school PC. The new per-row subqueries on the two views (`StudentTable.position`, `paidUpTo`, `paidUpToState`, `feeStatus`, `earlierPending`, five correlated lookups per row) are the likely cost, and no automatic run would catch a slowdown.
- Fix: Join `v_fee_position` once per row instead of five subqueries (plan spike (c) "one join per row"), or record it as an accepted F11 risk in the decision log.

## Minor

**M1.** `frontend/src/app/features/finance/receipt-form/receipt-form.component.ts:269-279`: The amount lines in the confirmation dialog come from the browser's parse of the typed text. Only the total and the words come from the server. The plan (first tasks, item 4) says "the confirmation dialog repeats server-returned values". Fix: return the checked `registration` and `fee` in `ReceiptPreviewDto` and show those.

**M2.** `frontend/e2e/receipt-entry.spec.ts:138` and `:157`: `expect(page.getByRole('alert').first()).toBeVisible()` passes for any alert on the page, so the "both amounts zero" and "missing cheque details" cases do not check the message or the field. Fix: filter by the expected text or by the field's error element.

**M3.** `management/src/main/java/com/admin/management/fees/FeesAudit.java:231`: The Javadoc of `chequeNotCleared` cites "(FR-011)". It should cite FR-013 and FR-021. Lines 108 and `Money.java:6` carry F2 FR numbers in an F3 context. Fix: correct the FR references.

**M4.** `frontend/src/app/features/finance/receipt-print/receipt-print.component.ts:23-38, 113`: The print stylesheet hard-codes `white`, `black` and `gray` and uses an inline `style` for the signature line. This is probably intended for black-on-white printing, but it bypasses the theme-token rule. Fix: add print tokens (for example `--color-print-ink`) to `styles.css`, or record the exception for the print sheet.

**M5.** `frontend/src/app/features/students/student-profile/student-profile.component.html` (receipts table, `data-testid="receipt-link"`, `py-1` rows) and `receipt-form.component.html:51` (`use-previous-years` link button): these targets are below 44px. Fix: give the link cells or links `min-height: var(--spacing-control)`.

**M6.** Spec FR-021 says the export audit entry is written "in the same transaction". The code uses `recordIndependent` after the file is written (plan Constitution Check III; F0 pattern). The behaviour is reasonable but the spec text does not match it. Fix: align the FR-021 wording with the plan.

**M7.** `management/src/main/java/com/admin/management/fees/ReceiptService.java:127`: The duplicate-number message prints the date in ISO form ("receipt of 2026-10-30"). Every other screen uses "30 Oct 2026". Fix: format it as the UI does, or send the date as a field and let the screen format it.

**M8.** `management/src/main/java/com/admin/management/fees/StudentFeesService.java:59` uses a fully qualified `java.util.List`, and `StudentTable.java` uses `java.math.BigDecimal` and `java.time.LocalDate` inline. This is style only. Fix: import them.

**M9.** Housekeeping:
- `specs/004-fee-collection/spec.md:7` still says `Status: Draft`.
- `docs/decision-log.md` has two `## Open items` sections. This was already so on `main` and the branch edits both copies identically.
- `docs/runbooks/correct-receipt.md` does not warn that an apostrophe in `<reason>` must be doubled in SQL.

Fix: mark the spec Implemented, merge the duplicate section in a later docs change, and add the quoting note to the runbook.

### Checked with no finding

- **Money:** no `float` or `double` in main code. `Money.problem` refuses more than two decimals or a negative amount, and never rounds. DB CHECKs require `total = registration_paid + fee_paid`, `total > 0` and amounts `>= 0`.
- **Migrations:** `V8__receipts.sql` is the only migration change (status A). Every foreign key is `ON DELETE RESTRICT`. No cascade, and no hard delete of people.
- **Immutability:** row triggers refuse UPDATE and DELETE and statement triggers refuse TRUNCATE on both tables. `@Immutable` entities. No PUT, PATCH or DELETE endpoints (`ReceiptSecurityIT`, `ReceiptImmutabilityIT`).
- **Audit:** `FeesAudit.receiptCreated` and `chequeNotCleared` run inside the `@Transactional` service methods, and `AuditService.record` is MANDATORY. `void_receipt` writes its mark and its audit row in one function call.
- **Secrets and data:** nothing tracked matches `.env`, `credentials-first-run`, `backups/` or `real-data/`. Fixtures are synthetic (`Seedsurname`, `Test Bank`, `Test School`). The real school name in `application.properties` is public, and the plan names it as configuration.
- **History:** no merge commits on the branch. The reflog shows only `reset: moving to HEAD` (not a rewrite) and no force-push.
- **Security:** all new endpoints are under `/api` and authenticated. The CSRF refusal is tested. Refusal bodies are tested to carry no stack trace or SQL. Test-support endpoints are `@Profile("test")`, behind the existing `TestProfileGuard`.
- **SQL:** the strings built in `StudentTable` and `ReceiptTable` hold only constants, ids and ISO dates, and user values are bound as `?`.
- **Module boundaries:** `fees` uses `students.api` and `masterdata.api` only. `students` reads only `fee_accounts` and the two fee views, which `ModuleDependencyTest` enforces.
- **Frontend:** strict types with no `any`; labels on every input; loading, empty and error states on the form, view, register and profile; `btn-sm` keeps the 44px minimum height.
- **Existing tests changed:** the F2 assertions removed in `BroughtForwardIT`, `StudentFeesIT`, `FeeListColumnsIT`, `fees-student-views.spec.ts`, `students-shell.spec.ts` and `student-columns.spec.ts` were replaced by positive assertions, as FR-014 and FR-015 ("F2's hiding ... ends") require. None of these is weakened without a reason.

## Verify result

`bash scripts/verify.sh` was run by this review on 2026-10-30 (started 13:10, on HEAD `f6131c6`, clean tree). Exit code 0, `VERIFY OK`.

- Backend `./mvnw verify`: 666 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS
- Frontend unit tests: 34 files, 274 passed
- Playwright: 173 passed (8.1 min)
- FR coverage, strict: 46 of 46 overall; 25 of 25 for `004-fee-collection`

The `performance`-tagged timing tests are not part of this run (see S4).
