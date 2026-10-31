# Plan review: 004-fee-collection (F3 fee collection and receipts)

Reviewed: `specs/004-fee-collection/` (spec, plan, research, data-model, quickstart, contracts/openapi.yaml), the agreement v1.1, `docs/decision-log.md`, constitution 1.1.0, `CLAUDE.md`, `docs/design/ui-tokens.md`, `docs/design/ui-prototype.md` and the image `Fee collection.png`. I also read the F2 plan and data model and the merged code the plan builds on (`ApiExceptionHandler`, `CodedConflictException`, `TableRequest`, `TableEndpoints`, `ExcelExporter`, `AuditService`, `SensitiveMasker`, `StudentTable`, `V2__users_audit.sql`, `V7__fees.sql`, `AbstractIntegrationTest`, `pom.xml`, `frontend/package.json`). I did not run the build or any test, and I did not check library versions against the vendors.

## Verdict: NOT READY

Two findings change what the tasks must contain (B1, B2). Both are small edits to the plan and the contract; once they are made the verdict becomes READY WITH FIXES. The rest of the design (computed balances, the append-only receipt and mark tables, the per-student lock) is sound and consistent with the constitution.

Counts: Blocking 2, Should fix 11, Minor 8, Questions 5.

## Blocking

**B1. The task budget defers requirements that the spec says MUST be built.**
- Location: `plan.md:155` (deferral order); `spec.md:181` (FR-014), `spec.md:182` (FR-015), `spec.md:216` (SC-009), FR-024.
- Problem: the plan says to defer, if the list grows, "the Students list Fees status filter" and "the 'balance after' preview on the form beyond the total". FR-015 names the Fees status filter and FR-014 says the Collect fee screen MUST show the balance before and after. SC-009 and FR-024 require every FR to be covered, and `check-fr-coverage.sh --strict` would fail. The plan also says the deferred items are "not needed by an FR", which is not true for these two.
- Fix: delete those two items from the deferral list, or change FR-014 and FR-015 in the spec first (with the owner's agreement); keep only the on-screen day-wise panel as deferrable, which no FR requires.

**B2. The contract gives the Mark action no way to get its balance token, and the receipt has no student link.**
- Location: `contracts/openapi.yaml` schema `Receipt` (line 225), `NotClearedInput`, `GET /receipts/{serialNo}`; `research.md` R3 (line 26); spec FR-013, FR-016.
- Problem: `POST /receipts/{serialNo}/not-cleared` requires `balanceVersion`, but the receipt response has neither `balanceVersion` nor `studentId`. The receipt view can only get the token by calling `GET /students/{id}/fee-collection`, and it cannot know the student id from this response (the register row has none either). The stale pop-up for a mark (FR-016) cannot be built as the contract stands.
- Fix: add `studentId` and `balanceVersion` to the `Receipt` response (and `studentId` as an internal column of the register), or state that the receipt view calls the fee-collection endpoint and add the id to the response.

## Should fix

**S1. The decision-log and agreement changes are scheduled last, but the constitution and the plan itself need them first.**
- Location: `plan.md:153` (the third docs task) against `data-model.md:140` ("decision log entry to be written with the first task") and `plan.md` Complexity Tracking; constitution "Technology & Data Constraints" (specs must not contradict the agreement without a recorded decision).
- Problem: the spec already departs from the agreement in at least five ways: no `paid_up_to` on the receipt (agreement 4.4), no DUPLICATE mark (section 8), a third status Voided (agreement lists two), a number reusable after a void, a table split for the status. The decision-log row of 2026-10-28 covers none of the last four. The Students-list view exception also needs its row "before the Students list task starts". The plan puts all of it in the last group.
- Fix: make "decision-log rows, agreement section 4.4 and 8 and 11 notes, constitution Complexity row" the first docs task and run it before any task that depends on them; list the rows to write.

**S2. The plan does not say how the Fee collection screen differs from the mockup, and four differences conflict with the decisions.**
- Location: `docs/design/CampusFlow UI Prototypes-png/Fee collection.png`; `ui-prototype.md` rule "build the screen to match its prototype image"; `plan.md` structure (lines 104-117), `quickstart.md` section 4.
- Problem, from the image: (a) it has one "Amount received" field, the plan has a registration row and a year-fee row (a decision, but not recorded as a deviation); (b) a link "Need to cancel a receipt? Open the receipt and give a reason" which contradicts FR-009 and must not be built; (c) a note that the fund ledger is updated on save, which belongs to F9 and must stay hidden; (d) a "Serial no. (automatic)" field showing the next number before saving, which the plan does not provide (the serial is an identity assigned on insert, and showing a guess would be wrong under concurrency); (e) a "Net payable, Paid so far, Pending, Due date" strip, but `Balance` in the contract has no due date; (f) one "Save and print receipt" button, whereas the plan has Save, a confirmation dialog, then a print view.
- Fix: add a short "differences from the prototype" list to the plan (or `ui-prototype.md`), add `dueDate` to the contract, and say the serial no. field is shown only after saving.

**S3. No colour tokens are named for the new status tags, and the tokens file has none for them.**
- Location: `docs/design/ui-tokens.md` (no paid, partial or shortfall pair; `ui-prototype.md` says the prototype's hex values are not used); `plan.md` Frontend rows.
- Problem: the screens add tags Valid, Cheque not cleared, Voided by correction, Paid, Partly paid, Unpaid, and the red or amber pending figure. The F2 plan reused the F0 warning and accent-soft pairs; this plan says nothing, so each screen will pick its own colours.
- Fix: map each tag to an existing token pair (for example warning for Cheque not cleared, error for Voided, accent soft for Valid) in the plan, so the theme conformance test can cover it.

**S4. The developer void is a manual database call, which sits uneasily with Principle V and the "similar to the password reset" style the owner asked for.**
- Location: `research.md:77` (R9), `data-model.md:92`, spec FR-022; constitution Principle V ("manual changes to a database are prohibited"), `docs/runbooks/reset-admin-password.md` (done through the application with a start-up setting).
- Problem: the password reset runs through the application and writes its own audit row with a user; the void is `SELECT void_receipt(...)` in `psql`. (a) The principle's wording is about schema, but a manual data change is what it names. (b) The audit row has no `user_id`; "who" is a free-text argument, against Principle III's "who". (c) The function is executable by the application's own database login, so only a source-scan test stops a future bug or an injection from voiding a receipt. (d) The target PC runs PostgreSQL as a Windows service (decision log 2026-10-25); whether `psql` is installed there is not verified.
- Fix: either state in the plan that the owner accepts a developer-run function as the one permitted manual data change (record it with S1), or make the void a start-up setting like the password reset; in either case record the developer's name in a field the function cannot leave empty and check that `psql` is available on the F11 installer.

**S5. The "paid up to" meaning is ambiguous and the cap can contradict the formula.**
- Location: `research.md` R4 (cap and examples), spec FR-012 and US2 scenario 1, quickstart scenario 6.
- Problem: with `start + days`, paying one day's fee gives "paid up to 2 April", which reads as 2 April being paid, while only 1 April is. The plan caps at `start + days - 1` only when rounding reaches the last day, so a payment of 99.9% shows the last day (fully covered) while 364 whole days shows the 364th day after the start, one day earlier than the "last paid day" reading would give. The meaning is never stated.
- Fix: state in the spec whether the date is the first unpaid day or the last paid day, and make the formula, the cap and the worked examples agree (see Q1).

**S6. The balance token rests on two things the plan did not check.**
- Location: `research.md:26` (R3), `data-model.md` "Balance token"; F2 `FeeAccountSync` (I found no `updatedAt` handling when I searched for it).
- Problem: the token uses `fee_accounts.updated_at` as the sign of a revise. If an unchanged account is rewritten, the token changes for nobody to blame (a false pop-up); if a changed account keeps its time, a real change goes unseen. The plan also assumes the `Instant` microsecond value is stable between reads; I did not verify it.
- Fix: add to "first tasks" a check of when `updated_at` changes and a test that a revise changes the token only for affected accounts.

**S7. FR-015 and the plan list different columns.**
- Location: `spec.md:182` (FR-015: Total fee, Paid, Pending, Paid up to, Fees status filter); `plan.md` structure line 80 and `data-model.md` ("Students list"): adds "Earlier years pending" and a Fee status column; contract `StudentSearchRow`.
- Problem: the owner approved the planned columns, but the spec text still lists four and a filter, so `@Covers` tests written from the spec will not name the extra column.
- Fix: update FR-015 to list the columns and the status words.

**S8. The previous-years receipt snapshot is not defined.**
- Location: `data-model.md` (`standard_id`, `division_id`, `category_id`: "the class record's values when saved; null only for a previous-years receipt of a student with no class record"); spec FR-010.
- Problem: for a previous-years receipt, which class record is meant (the current one, the most recent, the chosen earlier year's) is not said, yet the register's Std / Div column and the standard filter use it.
- Fix: state the rule (suggest: the student's shown record, as the Students list does) and test it.

**S9. Contract gaps beyond B2.**
- Location: `contracts/openapi.yaml`.
- Problem: there is no endpoint or reference for the student search on the form, nor for the register's standard and status filter choices (F1 has a filter-options source, not cited); `FeeCollection` returns no year dates; `ReceiptRefused` lists the codes but does not say which field `OVERPAYMENT` points to for a previous-years receipt (it has no registration row).
- Fix: cite the F1 student search and F0 filter-options endpoints, and say which field `OVERPAYMENT` points to for each receipt type.

**S10. Data model against the agreement: the differences list is incomplete.**
- Location: `data-model.md:140` against agreement section 4.4 "Receipts".
- Problem: the list omits that the agreement has `class_snapshot` as one field (the plan has ids and names), `receipt_type` as "Auto" (it is an input here), the cheque fields as "Optional" (the plan requires them for Cheque), `status` with two values (the plan has three), and `academic_year` as "Auto" (the previous-years receipt takes a chosen year). The agreement's section 4.1 diagram is also not mentioned.
- Fix: add these to the differences paragraph so the agreement note in section 11 is complete.

**S11. The students list will carry five more correlated subqueries per row.**
- Location: `plan.md` Complexity Tracking, `research.md` R11; F2 `StudentTable.SHOWN_RECORD`.
- Problem: each new column repeats the "shown record" subselect and a view lookup; with Total fee and Fee state that is about seven per row on 10,000 students. The spike is planned (good), but its fallback (a stored summary) contradicts FR-016, and no cheaper alternative inside the rule is named (one lateral join returning all the figures once per row).
- Fix: add "one join per row, not one subquery per column" as the first thing to try in the spike, before the fallback.

## Minor

**M1. "4 new screens" lists three.** `plan.md:41` (collect fee, receipt view and print, receipt register). Fix: say 3, or list print separately.

**M2. Task numbers that do not exist yet.** `plan.md:37` and Q5 history refer to "T002", and research mentions "F2 T007". Fix: refer to the group name.

**M3. PostgreSQL 15 is carried over, not verified.** `plan.md:29`; `specs/001-foundation/plan.md:22` says 15, `deploy/docker-compose.yml` has a commented `postgres:16`. The advisory-lock function `hashtextextended` needs 11 or later, so both work; say which is used. Spring Boot 4.1.2, Angular `^22.2.0`, Tailwind `^4.3.3` and POI 5.5.1 match `pom.xml` and `package.json`; I did not check them against the vendors.

**M4. SC-001 has no test.** `spec.md` SC-001 ("under 1 minute"); no task or quickstart step measures it. Fix: mark it a manual check in `quickstart.md`.

**M5. Test isolation with immutable tables is not explained.** `AbstractIntegrationTest` cleans the schema once per class (`@BeforeAll`), and the triggers forbid deleting receipts, so tests in one class share receipts. Fix: say that tests use unique students, years and receipt numbers (as `FeeTestData` does for years), and that the trigger tests use their own class.

**M6. Print cannot be tested by clicking.** `plan.md` e2e group; `quickstart.md` scenario 16. Fix: say the e2e test emulates print media and reads the page content (Playwright can do both); the browser print dialog is not driven.

**M7. A fee revise and a receipt are not serialised.** F2's revise locks the year's status row; a receipt takes the student lock. A revise that lowers a fee at the same moment as a receipt gives "excess paid", which the spec accepts (FR-014); say so in R3 so it is a decision, not an oversight.

**M8. Receipt number text.** `research.md` R12: numbers "03100" and "3100" are different receipts. The spec leaves leading zeros to the admin; add a one-line test and a note in the quickstart.

## Checks that passed (for the record)

1. **FR and SC coverage**: FR-001 to FR-025 each appear in `research.md` R15 or the contract; the exceptions are the deferred items in B1, and SC-001 (M4). SC-002 to SC-009 map to tests or the performance task.
2. **Constitution**: I (`NUMERIC(12,2)`, CHECKs in `data-model.md`, one `HALF_UP` step), II (triggers, separate mark, void as a reversal), III (audit in the same transaction; the print and the void caveats are S4), IV (all keys `ON DELETE RESTRICT`), V (V8 only, next free after V7), VI (synthetic data, no sensitive field; `SensitiveMasker.MASKED` holds none of the receipt fields I checked), VII (the one extension is named and needs the owner's row, S1), VIII (R15 map). I found no missing principle.
3. **Migrations**: V8 is the next free number; nothing edits V1 to V7; the audit table is not changed, and its action list already holds Create, Edit and Export (`V2__users_audit.sql`). `void_receipt` inserts into `audit_log`, whose trigger only blocks update and delete.
4. **Security**: session and CSRF are inherited; the new endpoints are under `/api`; the error messages name a receipt's date and serial no. only to the signed-in admin; no secret or password is stored; CORS is unchanged; the advisory lock key is built from the receipt number, which is not a secret. The residual risk is S4(c).
5. **Design**: the Finance tab with one pill matches the prototype; the Fee tracker, Payroll and Expenses pills stay hidden as the F1 rule says.

## Questions for the owner

**Q1.** Should "Fees paid up to" be the first day not yet paid (1 April + 100 days for 100 days of fee) or the last day paid (1 April + 99 days)? It decides every date on the profile and the list (S5).

**Q2.** The mockup shows "Need to cancel a receipt? Open the receipt and give a reason". Confirm the link is removed everywhere, and that the receipt view offers only Mark Cheque not cleared (S2).

**Q3.** Is a developer-run `void_receipt` in `psql` acceptable as the one manual data change, or should the void work like the password reset (a start-up setting)? Does the school computer have `psql` (S4)?

**Q4.** For a previous-years receipt, which class record should the receipt's Std / Div show: the current one, the most recent, or none (S8)?

**Q5.** Plan Q10 is still open: may a receipt be dated before its academic year starts?
