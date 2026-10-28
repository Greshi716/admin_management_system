# Plan review: 003-fees (F2 fee structure and student fee accounts)

Reviewed: `specs/003-fees/` (spec, plan, research, data-model, quickstart, contracts/openapi.yaml), the agreement v1.1, `docs/decision-log.md`, constitution 1.1.0, `CLAUDE.md`, `docs/design/ui-tokens.md` and `ui-prototype.md`. I also read the merged F1 code on this branch (`ApiExceptionHandler`, `ConflictException`, `ExcelExporter`, `ColumnDef`, `SensitiveMasker`, `StudentService`, `StudentTable`, `ModuleDependencyTest`, the audit-log frontend) to check what the plan says about it. I could not run the build or tests, and I could not check library versions against the vendors.

## Verdict: READY WITH FIXES

No finding blocks writing tasks, but S1 to S5 change what the tasks must contain and should be settled first.

Counts: Blocking 0, Should fix 10, Minor 9, Questions 4.

## Blocking

None.

## Should fix

**S1. The error contract needs code the plan assumes already exists.**
- Location: `plan.md` decisions 2 (line 106); `research.md` R4 (line 23) and R8 (line 47); `contracts/openapi.yaml` `ReviseOrDuplicate` response.
- Problem: R8 says to throw `ConflictException` "with code `FEE_STRUCTURE_FINALISED`", but F1's `ConflictException` has only a field and a message, and `ApiExceptionHandler.conflict` sets no code and no `current`. `REVISE_CONFIRMATION_REQUIRED` needs both a code and `current: { studentCount }`. `ApiProblem` can carry them; only the exception and handler lack them.
- Fix: add a coded conflict exception (code, field, message, current) and its handler to the plan's file list, and a test for the 409 body.

**S2. Excel export of money does not do what R9 says.**
- Location: `research.md` R9 (line 53) and `plan.md` first task 2 (line 117); `ExcelExporter` (the `BigDecimal` branch writes `toPlainString()` as a text cell).
- Problem: the plan says `totalFee` is exported "as a number with two decimals" and lists this only as something to confirm. The code already shows `BigDecimal` is written as text, and any other `Number` is written with `longValue()`, which drops decimals. A text cell cannot be summed in Excel, and a non-`BigDecimal` number loses its paise.
- Fix: decide now to change the shared exporter to write `BigDecimal` as a numeric cell with a `0.00` format (see Q3), and add that change and its test to the plan; this touches F0 and F1 export tests.

**S3. A duplicate fee row will not show a message beside the field.**
- Location: `data-model.md` line 31 (two partial unique indexes); spec US1 scenario 3 (line 34); `ApiExceptionHandler` `KNOWN` map.
- Problem: unique violations only map to a field when the constraint name is in `KNOWN`; otherwise the answer is a generic 409 with no field. The plan names no constraint names and does not mention `KNOWN`.
- Fix: name the two indexes in the data model, register both in `KNOWN` (field `categoryId` or `standardId`), and also pre-check in the service so the message is the same.

**S4. The contract does not describe the F1 changes it depends on.**
- Location: `contracts/openapi.yaml` lines 86 to 96; `plan.md` line 76; `research.md` R8.
- Problem: `EnrollmentDto.feeLocked` (needed so the dialog can show the message before submit, spec FR-008) and the new list columns `totalFee` and `feeState` are described only in prose, not as schemas. The fee screen also needs the active standards and categories for the row form and the list of academic years for the picker; no endpoint or reuse of an existing one is stated (F1's `student-pick-lists` returns divisions too and is student-oriented).
- Fix: add the `feeLocked` field to a changed `Enrollment` schema, list the two list columns, and say which existing endpoints supply the year picker and the standard and category choices (or add one).

**S5. Stale-save protection for the structure is thinner than FR-013 and the contract imply.**
- Location: spec FR-013 (line 153); `contracts/openapi.yaml` `FeeRowInput` (no structure version); `research.md` R4 and R7.
- Problem: only the row `version` is sent. If window A finalises the year and window B still shows Draft, B's add or edit arrives at a Finalised year; the server answers with the revise warning, and B's admin may confirm a change they believed was a plain Draft edit. Nothing tells them the structure changed. Revise also does not bump the status `version`, so the structure-level check only protects the Finalise call.
- Fix: send the structure `version` (or the expected status) with every row request and return `STALE_VERSION` when the status changed, so B reloads before any revise warning.

**S6. Constitution III: per-student fee changes have no audit record of their own.**
- Location: `plan.md` line 48; `research.md` R5 (rejects per-student entries); `data-model.md` Audit table.
- Problem: a revise changes what every student owes, but the audit has one entry with the old and new values of the row and a count. Principle III asks for the before and after values of every financial write. A student's account before and after can be re-derived only if the old rows are still known, which they are in the entry, but the plan does not say that is the justification.
- Fix: state in the Constitution Check that the row's old and new values plus the count are the audit record for the derived accounts and why that is enough, or record "accounts changed" (count of accounts whose amount actually changed) in the entry. See Q1.

**S7. Test helpers the quickstart relies on are not planned.**
- Location: `quickstart.md` scenarios 10, 14, 19 (lines 36, 40, 45); `plan.md` source tree.
- Problem: three scenarios use "test helper" for calling the API without `confirm`, sending a forbidden class record change, and loading 2,000 class records and 10,000 students. The source tree lists no test-support code (F1 added `StudentTestSupportController` for this).
- Fix: add a test-profile support endpoint or test fixture class for seeding class records, and note that it must stay behind the `test` profile guard.

**S8. The agreement's data sections are not updated for the new tables.**
- Location: agreement 4.1 diagram, 4.4 (lines 316 to 327); `data-model.md` lines 5 to 60.
- Problem: the agreement describes a fee account with a `carried_forward` flag and one brought-forward row per account; the plan drops the flag, adds `registration_fee` to the account, and adds `fee_year_status` and `fee_brought_forward`. The decision log of 2026-10-26 covers the rules, not the table shapes. The constitution says specs must not contradict the agreement without a recorded decision.
- Fix: add a decision-log row (or update 4.4 and the 4.1 diagram) that records these table differences.

**S9. Concurrency and locking are asserted, not shown to work.**
- Location: `research.md` R7 (line 41); `plan.md` decision 8 (line 112).
- Problem: `FOR UPDATE` on the status row and `FOR SHARE` in `classRecordAdded` are described, but the status row may not exist yet, and the spike (first task 3) measures speed only. Whether Spring Data's JPA locking maps to these statements (and works alongside `@Version`) is not stated.
- Fix: say which locking API is used and add a test that a registration during a finalise ends with an account for the new record.

**S10. SC-001 and part of SC-006 have no check.**
- Location: spec lines 176, 181; `quickstart.md`.
- Problem: SC-001 (12 standards in under 10 minutes) has no scenario. SC-006 says amounts are "stored and shown with exactly two decimals"; scenario 2 tests refusal, but nothing in the quickstart checks the display of 28,000.00 or the stored scale.
- Fix: add a quickstart row for each, or mark SC-001 as a manual timing check.

## Minor

**M1.** `plan.md` line 105 and first task 1 (line 116) still say F1 must be rebased in; F1 is merged now. Update the wording.

**M2.** Spec FR numbering runs FR-010, FR-013, FR-011, FR-012 (lines 152 to 158). Reorder or note it.

**M3.** `contracts/openapi.yaml` line 154 has the state `NO_RECORD`, which the spec never describes (a student always gets a class record at registration). Remove it or define it.

**M4.** FR-002 asks for a confirmation on Finalise, but the endpoint takes none; the confirmation is screen-only. Acceptable for one admin; say so in the spec or plan.

**M5.** `data-model.md` `fee_accounts` (lines 33 to 46) says `academic_year_id` is kept "for year-wide updates and counts" but lists no index on it. Add one, or say the primary key is enough.

**M6.** Technology versions (Spring Boot 4.1.2 in `pom.xml`, Angular ^22.2.0 and Tailwind ^4.3.3 in `package.json`) are inherited from F0 and F1; the plan does not state they were re-verified and I could not verify them. Not a new risk for this feature. PrimeNG is not used (dropped on 2026-10-25).

**M7.** Money in JSON is a number (R3). The frontend must not add amounts itself and must format to two decimals (trailing zeros are lost when parsed). Add this to the frontend tasks.

**M8.** `ModuleDependencyTest` reads only import lines, so the approved SQL read of `fee_accounts` by `students` is not guarded. A short test or comment that lists the allowed cross-module reads would keep Principle VII's exception from growing. Also the test's `PUBLIC` list needs `students.api.`.

**M9.** Spec FR-009 defines total due as year fee plus registration fee plus brought-forward, but not what it is when the fee is not set or not finalised. The data model (line 86) decides "brought-forward alone"; the spec should say it.

**M10 (design).** The status tag (Draft / Finalised) and the revise warning have no tokens in `docs/design/ui-tokens.md`. The warning panel tokens (bg `#FFF3DC`, text `#6B4200`) already exist; name them. The prototype's single "Save fee structure" button becomes a save per row; the spec records the grid difference but not this one.

## Questions for the owner

**Q1.** Is one audit entry per row change (old and new row values plus the count of students recalculated) enough for the derived fee accounts, or do you want the number of accounts whose amount actually changed recorded too? (S6)

**Q2.** Should a revise be findable by the Action filter in the audit log? Today it is Create, Edit or Delete with the reason `Revise`; a separate action `Revise` needs a change to the audit table's CHECK list (F0 FR-027 says no structure change).

**Q3.** May the shared Excel exporter change to write amounts as numeric cells with two decimals for every list? (S2)

**Q4.** The brought-forward note is stored in full in the audit log. Is it all right if it holds personal remarks about a family, or should notes be limited to a short reason and kept out of the audit log?

## Coverage and checks (for reference)

**1. FR and SC coverage.** FR-001 to FR-014 each appear in the plan, data model, contract or quickstart. Uncovered or weakly covered: SC-001 and the display half of SC-006 (S10). Spec edge cases (inactive masters, Left students, no current year) are in the quickstart scenarios 17 and 7.

**2. Constitution (evidence in `plan.md` lines 44 to 53).**
- I: present, and the `BigDecimal` and `NUMERIC(12,2)` evidence is concrete. The Excel text cell weakens it (S2).
- II: present; it argues recalculation is not a change to a finalised record. It would be more honest to say Revise is the agreement's own exception (M-level wording).
- III: present, with the gap in S6.
- IV: present; fee rows are deleted as setup data and no person record is.
- V: present (V7 only; no edit of V1 to V6).
- VI: present.
- VII: present, with the owner-approved exception; see M8.
- VIII: present; the quickstart section 4 lists the checks.

**3. Technology.** No new library. Versions inherited (M6). The pieces work together as in F1.

**4. Data model vs agreement.** `fee_rows` matches agreement 4.2 (category optional, amount required, due date optional, registration fee optional). `fee_accounts` differs from 4.4 (no `carried_forward`, adds registration fee); new tables are not in the agreement (S8). All amounts are `NUMERIC(12,2)` with CHECK `>= 0`. All foreign keys are `ON DELETE RESTRICT`; nothing cascades. The audit table is not touched, so it stays append-only.

**5. API.** Every FR with a user action has an endpoint. Gaps: S1, S4, S5. Names are consistent with F1.

**6. Security.** New endpoints sit behind the existing session and CSRF rules (I did not re-read `SecurityConfig`). No secrets or sensitive fields are added to logs. The 409 body exposes only a student count. No CORS change. Note Q4.

**7. Migrations.** One new Flyway migration, `V7`, ordered after F1's `V6`. No seed data. Test databases are reset by a Flyway clean that needs no change.

**8. Testability.** Each FR has a way to be tested; fixtures are synthetic. See S7 for the missing helpers.

**9. Risks.** S5, S9, and the unproven performance of the `fee_accounts` subquery in the list (a spike is planned). Over-engineering: none found; the shared interface and `students.api` are the smallest way to avoid a cycle.

**10. Design.** M10.
