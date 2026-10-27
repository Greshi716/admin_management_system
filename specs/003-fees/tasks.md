---

description: "Task list for F2 Fee Structure and Student Fee Accounts"
---

# Tasks: Fee Structure and Student Fee Accounts (F2)

**Input**: Design documents from `/specs/003-fees/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Included, written together with the code in the same task (constitution Principle VIII, FR-014): in every task write the test first, see it fail, then write the code. Backend tests carry `@Covers("FR-xxx")`; Playwright test titles start with the FR ids. One task covers one group of files, never one class.

**Organization**: Grouped by user story. Shared plumbing (schema, coded errors, exporter, module guard, entities, selection rule, test data) is in Phases 1 and 2. Each story has a backend task and a frontend task.

**Review**: this list was revised after `docs/reports/003-fees/review-tasks.md` (B1, B2, S1 to S9, M1 to M8 are resolved here).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US5, mapping to the user stories in spec.md
- **[PLAN MODE]**: start the task in plan mode (CLAUDE.md: money, schema), show the plan, and wait for approval before editing
- Commit after every task, with the FR ids in the message (CLAUDE.md). Never commit to `main`. No task here opens a pull request.

## Path Conventions

- `BE/` = `management/src/main/java/com/admin/management/`; `BT/` = `management/src/test/java/com/admin/management/`
- Migrations: `management/src/main/resources/db/migration/`
- `FE/` = `frontend/src/app/`; e2e specs in `frontend/e2e/`, e2e helpers in `frontend/e2e/support/`
- Follow the nearest F0 or F1 example for each kind of file (for example `BT/students/StudentEditIT.java`, `FE/features/students/`)
- Synthetic data only (constitution VI). Money is `BigDecimal` and `NUMERIC(12,2)`; compare with `compareTo`, never `equals`; no `double` or `float`, in tests too
- Needed on the machine: JDK 21, PostgreSQL with the databases `admin_management_db` and `admin_management_test`, Node as in F0, and port 8080 free for the Playwright run
- File names used below are the plan's: `BE/fees/` holds `FeeRow`, `FeeYearStatus`, `FeeAccount`, `BroughtForward` (entities) with their repositories, `FeeSelection`, `FeesAudit`, `FeeStructureService`, `FeeStructureController`, `FeeClassRecordHooks`, `StudentFeesService`, `StudentFeesController`, `BroughtForwardService`, `BroughtForwardController` and the dto records. Where a name is not in the plan the task says "new file"

---

## Phase 1: Setup

- [X] T001 Confirm the starting point. First bring local `main` up to date, without touching the working tree: `git fetch origin main:main` (fast-forward only; stop and report if it refuses). Then check that `git log --oneline -3` shows the F1 merge (`ece1410`) under this branch and that `management/src/main/resources/db/migration/V6__students.sql` and `BE/students/` exist, and run `scripts/verify.sh` (Git Bash). Expected result: every backend and frontend test passes; the "FR coverage on branch 003-fees" line is RED for FR-001 to FR-014 (no F2 test exists yet; before `main` was updated the script gave a false "14 of 14" because it counted F1's tests, review B1). Record the pass counts and the red coverage line in `specs/003-fees/research.md` under a new heading "Starting point". If any test fails, stop and report; fix nothing in this task. (plan first task 1)
- [X] T002 Plan checks, in one throwaway test `BT/fees/FeePlanChecksIT.java` (delete it at the end of the task, keep the results): (a) `SensitiveMasker.MASKED` in `BE/shared/masking/SensitiveMasker.java` holds none of `amount`, `registrationFee`, `note`, `noteChanged`, `studentsRecalculated`, `accountsChanged`; (b) an `ApiProblem` with `current = Map.of("studentCount", 5)` serialises as `{"studentCount":5}` through `ApiExceptionHandler`; (c) Hibernate `LockModeType.PESSIMISTIC_WRITE` and `PESSIMISTIC_READ` on an existing entity (use `AcademicYear`) produce `for update` and `for share` on PostgreSQL 15 (log the SQL; if different, record the real SQL and prove with two connections that the second one blocks) and work together with `@Version`. Write the three results into `specs/003-fees/research.md` (R4, R7). (plan first task 2)
- [X] T003 Performance spike, throwaway `BT/fees/FeeSpikeIT.java` using `JdbcTemplate` and ordinary scratch tables created in the test and dropped at its end (not `TEMPORARY` tables: the connection pool hands out different connections, review S5; no migration yet): (a) 2,000 synthetic class records, 12 standards, 7 categories: compute every account in memory with a plain selection rule and batch-upsert them into a scratch table, twice (a finalise, then a revise of one row that changes about 10% of the accounts); each must finish in under 10 s (SC-004); (b) F1's list query shape with 10,000 students and 2 to 3 class records each, plus a correlated subquery on a scratch `fee_accounts_spike(enrollment_id primary key, amount numeric(12,2), registration_fee numeric(12,2))`: page, sort by the new column and filter each under 2 s, and the Excel export of all 10,000 rows under 30 s (F1 SC-003). Record the timings in `specs/003-fees/research.md` R7 and R9, noting they are from the development PC, not the 4 GB target. If a goal is missed, stop and report with the options (JDBC batch, summary column); do not choose silently. Delete the test after recording. (plan first task 3; FR-006, FR-010)
- [X] T004 [PLAN MODE] Write `management/src/main/resources/db/migration/V7__fees.sql` from `specs/003-fees/data-model.md`; show the full SQL in the plan and wait for approval before creating the file or running Flyway. Tables and constraints, quoted from the data model: `fee_year_status` (`academic_year_id` PK FK `academic_years`; `status` text NOT NULL default `Draft`, CHECK in (`Draft`, `Finalised`); `finalised_at` timestamptz null while Draft; `version` bigint NOT NULL); `fee_rows` (`id` identity PK; `academic_year_id`, `standard_id` required FKs; `category_id` optional FK `student_categories`; `amount` numeric(12,2) NOT NULL CHECK `>= 0`; `registration_fee` numeric(12,2) optional CHECK `>= 0`; `due_date` date optional; `version`; unique partial indexes `uq_fee_rows_category` on `(academic_year_id, standard_id, category_id)` where `category_id IS NOT NULL` and `uq_fee_rows_no_category` on `(academic_year_id, standard_id)` where `category_id IS NULL`; index on `(academic_year_id)`); `fee_accounts` (`enrollment_id` PK FK `student_enrollments`; `academic_year_id` required FK; `amount` numeric(12,2) null = "Fee not set", otherwise `>= 0`; `registration_fee` numeric(12,2) null = no registration fee, otherwise `>= 0`; `due_date` date; `updated_at` timestamptz NOT NULL; index `(academic_year_id)`); `fee_brought_forward` (`student_id` PK FK `students`; `amount` numeric(12,2) NOT NULL CHECK `>= 0`; `note` text CHECK `char_length(note) <= 200`; `updated_at` timestamptz NOT NULL; `version`). Every foreign key `ON DELETE RESTRICT`; no change to `audit_log`; do not edit V1 to V6 (the `protect-migrations` hook). After approval: apply on an empty database and on a V6 database, and add `BT/fees/FeeSchemaIT.java` that checks each constraint above with a rejected insert: negative amount, a value that overflows `numeric(12,2)` (10,000,000,000.00), a 201-character note, a duplicate row with and without a category, a wrong status. Do NOT test a three-decimal amount here: PostgreSQL rounds it to fit `numeric(12,2)` instead of rejecting it (review B2), so that refusal is the service's job and is tested in T012. (FR-001, FR-006, FR-007, FR-011; constitution V)

---

## Phase 2: Foundational (blocks all stories)

- [X] T005 [P] Coded conflict errors: new file `BE/shared/web/CodedConflictException.java` (code, field, message, optional `current`); handle it in `BE/shared/web/ApiExceptionHandler.java` (409, `ApiProblem` with `code`, `current` and a field error when a field is given); add `uq_fee_rows_category` (field `categoryId`) and `uq_fee_rows_no_category` (field `standardId`) to the `KNOWN` map with the message "A fee row for this standard and category already exists for this year." Test `BT/shared/web/CodedConflictExceptionTest.java` (or the F0 handler test file, extended): the 409 body for `REVISE_CONFIRMATION_REQUIRED` with `current.studentCount`, for `FEE_STRUCTURE_FINALISED`, and a duplicate-row unique violation mapped to its field. (FR-004, FR-008, FR-001; plan review S1, S3)
- [X] T006 [P] Excel money as numbers: change `BE/shared/excel/ExcelExporter.java` so a `BigDecimal` is written as a numeric cell with data format `0.00` (it writes text via `toPlainString` today) and any other `Number` is written without `longValue()` truncation, for every list. Update the F0 and F1 tests that expect text: `BT/shared/excel/ExcelExporterTest.java`, `BT/shared/TableExportIT.java`, `BT/students/StudentListIT.java` (search for string comparisons of exported amounts) and `frontend/e2e/support/xlsx.ts` with the specs that use it (`frontend/e2e/table.spec.ts` and the others that open an export). Run the whole F0 and F1 export suites and `frontend/e2e/table.spec.ts` before committing, since every list uses this class. Add a test: a `BigDecimal` 28000.5 is a numeric cell 28000.5 with format `0.00`. (FR-010, FR-011; plan review S2)
- [ ] T007 [P] Module plumbing: new `BE/shared/ClassRecordFees.java` (interface: `isLocked(Long yearId)`, `classRecordAdded(Long enrollmentId, Long yearId, Long standardId, Long categoryId)`); new public package `BE/students/api/` with `EnrollmentLookup` (class records of a year as `(enrollmentId, studentId, standardId, categoryId)`, the count, a student's shown record = current year's else most recent, and a record by student and year) and `StudentLookup.exists(Long)`, implemented in `BE/students/` over the existing repositories; extend `BT/shared/ModuleDependencyTest.java`: `PUBLIC` gains `students.api.`, `ALLOWED` gains `fees` -> `shared`, `audit`, `masterdata`, `students` and keeps `students` -> `shared`, `audit`, `masterdata` only; add a second test that scans the main sources for the SQL table names of each module (map to use: `fee_year_status`, `fee_rows`, `fee_accounts`, `fee_brought_forward` belong to `fees`; `students`, `student_enrollments`, `student_gr_numbers`, `student_photos` to `students`; `users` to `security`; `audit_log` to `audit`; the master data tables to `masterdata`) and fails on any read of another module's table except the allowed list: `audit_log` joins `users`; `students` reads `fee_accounts`. Put `@Covers("FR-014")` on this test class, as F1 did with FR-016. (FR-006, FR-008, FR-014; constitution VII)
- [ ] T008 [P] Fees entities and persistence: in `BE/fees/` create `FeeRow`, `FeeYearStatus`, `FeeAccount`, `BroughtForward` with their repositories (ids of other modules are plain `Long`; money `BigDecimal` with `@Column(precision = 12, scale = 2)`; `@Version` on `FeeRow`, `FeeYearStatus`, `BroughtForward`; a repository method on the status repository with `@Lock(PESSIMISTIC_WRITE)` and one with `@Lock(PESSIMISTIC_READ)`), and `FeesAudit` (area `Fees`; entities `FeeRow#id`, `FeeStructure#yearId`, `BroughtForward#studentId`; writes only changed fields like `StudentAudit`; for the brought-forward note the entry holds `noteChanged: true` and never the note text; the amount old and new in full). Test `BT/fees/FeeRepositoryIT.java`: `ddl-auto=validate` passes, a row round-trips with scale 2 (`28000.00`), a stale `@Version` save fails, and a changed note never appears in the stored `changes`. (FR-001, FR-006, FR-007, FR-011, FR-012)
- [ ] T009 [P] [PLAN MODE] Fee selection rule: new `BE/fees/FeeSelection.java`, a pure function over `(rows of the year, standardId, categoryId)` returning amount, registration fee and due date: the row with that standard and category, else the row with that standard and no category, else none ("Fee not set", no amount invented); registration fee from that applying row if it has one, else the blank-category row's, else none; due date from the row the amount came from. No rounding anywhere; totals are `add` of `BigDecimal`. Show the rule and the test table in the plan first. Test `BT/fees/FeeSelectionTest.java` with every example in the spec: Std 10 O/S with both rows, STAFF falling back, no row, registration fee on the category row, on the blank row, on neither, and a row with amount 0.00 (a fee of zero, not "Fee not set"). (FR-005, FR-011; SC-002)
- [ ] T010 [P] Test data: new `BT/support/FeeTestData.java` (builds a year with standards, divisions and categories, fee rows and N students with class records; a bulk generator for 2,000 and 10,000 records through the repositories); extend `BE/students/StudentTestSupportController.java` (`@Profile("test")` on `/api/test-support`, as F1 does) with an endpoint that seeds class records in bulk for the Playwright and quickstart scenarios 19 and 22; new `frontend/e2e/support/fees.ts` (helpers for the e2e specs: create a year with standards and categories, add and finalise fee rows through the API, seed students; modelled on `frontend/e2e/support/students.ts`). (FR-006, FR-014; plan review S7, review-tasks S1)

**Checkpoint**: schema, errors, exporter, module guard, entities, selection rule and test data exist and pass `./mvnw verify`.

---

## Phase 3: User Story 1 - Enter and finalise a year's fee structure (P1)

**Goal**: the admin builds a Draft structure and finalises it; finalising gives every class record of the year its fee account.

**Independent test**: quickstart scenarios 1 to 5.

- [ ] T011 [US1] Read side: `BE/fees/FeeStructureService.java`, `BE/fees/FeeStructureController.java` and dto records in `BE/fees/` for `GET /api/fee-structures/{yearId}` (status, `version` null while there is no status row, rows with standard and category names, `studentCount` = class records of the year through `students.api.EnrollmentLookup`) and `GET /api/fee-structures/{yearId}/choices` (active standards and categories through `MasterDataLookup.pickLists`, plus any standard or category an existing row of the year holds), per `specs/003-fees/contracts/openapi.yaml`. 404 for an unknown year. The fee API works for any year id; there is no server rule about the current year (that block is the front-end `currentYearGuard`, T015; review S2). Test `BT/fees/FeeStructureReadIT.java`: empty year, year with rows, inactive standard kept, names, count including Left students. (FR-001, FR-002, FR-013)
- [ ] T012 [US1] Rows: add `POST /api/fee-structures/{yearId}/rows`, `PUT .../rows/{rowId}` and `DELETE .../rows/{rowId}?version=&structureVersion=` to `BE/fees/FeeStructureService.java` and `BE/fees/FeeStructureController.java`. Rules: standard required; amount required, "zero or more, with at most two decimal places" (a value with more than two decimals after `stripTrailingZeros()` or a negative value is a 422 field error, never rounded: this is where the three-decimal test lives, review B2); registration fee and due date optional; one row per standard and category, pre-checked in the service with the same message as the unique-index mapping (T005); inactive standards and categories are refused for new rows (`MasterDataLookup.isStandardActive`, `isCategoryActive`) but an existing row keeps them; every row request carries `structureVersion`, a mismatch is `STALE_VERSION` with the latest structure, and every row change creates the status row on first use and bumps `fee_year_status.version` (take `PESSIMISTIC_WRITE` on it); every create, edit and delete writes a `FeesAudit` entry in the same transaction. In a Finalised year a request without `confirm=true` changes nothing and throws `CodedConflictException` `REVISE_CONFIRMATION_REQUIRED` with `current = { studentCount }` (final behaviour, not a placeholder; the confirmed path is T020). Test `BT/fees/FeeStructureIT.java`: spec US1 scenarios 1 to 3 and 6 (add, edit, delete in Draft, duplicate with and without category, amounts -1, 100.123 and missing), stale structure version, no `confirm` in a Finalised year (set the status with the repository in the test), audit rows, scale 2 in the response. (FR-001, FR-002, FR-003, FR-011, FR-012, FR-013)
- [ ] T013 [US1] [PLAN MODE] Finalise: add `POST /api/fee-structures/{yearId}/finalise` in `BE/fees/FeeStructureService.java` and `BE/fees/FeeStructureController.java`. In one transaction: lock the status row (`PESSIMISTIC_WRITE`), refuse when there are no rows (422) or the year is already Finalised, check `version` (stale), set `Finalised` and `finalised_at`, load the class records of the year through `students.api.EnrollmentLookup`, compute every account with `FeeSelection` (T009) and insert `fee_accounts` rows (batch; the method found in the spike T003), write one `Finalise` audit entry on `FeeStructure#yearId` with `studentsRecalculated`, `accountsChanged` and `rows`. The confirmation is on the screen only (spec FR-002). Show the transaction and the lock order in the plan first. Test `BT/fees/FeeFinaliseIT.java`: scenarios 4 and 5 and US2 scenarios 1 to 4 (category row, fallback, no row, registration fee), Left students included, 2,000 class records under 10 s (SC-004, reuse `FeeTestData`), audit counts, a failing audit write leaves the year Draft. (FR-002, FR-003, FR-005, FR-006, FR-012; SC-002, SC-004)
- [ ] T014 [US1] Front-end plumbing for the screen: `FE/features/setup/setup-layout.component.ts` (new group "Fees and salary" with the item "Fee structure"; the unbuilt Setup items stay hidden), new `FE/features/fees/fees.routes.ts` (route `/setup/fees`, behind `currentYearGuard` from `FE/core/auth/current-year.guard.ts`), new `FE/features/fees/fees.service.ts` (typed calls for every fee endpoint in the contract, amounts as numbers, errors through the existing `api-problem.ts`), the Setup route registration in `FE/app.routes.ts`. Unit test for the service (request shapes, a 409 `REVISE_CONFIRMATION_REQUIRED` and `STALE_VERSION` mapped to typed errors). (FR-013)
- [ ] T015 [US1] Fee structure screen: new `FE/features/fees/fee-structure.component.ts` (+ `.html`). Layout: the prototype's Setup card (title with the year, one line of help, status tag Draft or Finalised in the accent-soft tokens, year picker from the F0 academic-years list, table of rows: Standard, Category or "All categories", Yearly fee, Registration fee, Due date, a save per row, Add row, Finalise with a confirmation dialog); standard and category choices from `/choices`; amounts shown with two decimals and Indian digit grouping and never added in the browser; field errors beside the fields; stale response reloads the structure; controls at least 44 px; table scrolls sideways on a phone. After finalising, Add, Edit and Delete are replaced by Revise (behaviour in T021). Tests: unit tests for the component and `frontend/e2e/fees-structure.spec.ts` (titles start with FR ids, uses `frontend/e2e/support/fees.ts`): quickstart scenarios 1 to 5, 16 (two windows), 17 (no year covers today: the fee screen is blocked as in F0) and the layout assertions of quickstart section 4. (FR-001, FR-002, FR-003, FR-011, FR-013)

**Checkpoint**: a structure can be entered and finalised; accounts exist.

---

## Phase 4: User Story 2 - Each student gets a fee from the structure (P1)

**Goal**: fee accounts follow new class records, and the profile and list show them.

**Independent test**: quickstart scenarios 6, 11, 15, 21 to 23.

- [ ] T016 [P] [US2] (not parallel with T024, same `StudentService.java`) Accounts for new class records: new `BE/fees/FeeClassRecordHooks.java` implementing `shared.ClassRecordFees` (`isLocked` = year status is Finalised; `classRecordAdded` takes `PESSIMISTIC_READ` on the year's status row and, when Finalised, creates the account with `FeeSelection`; does nothing for a Draft year or a year with no status row); call it from `BE/students/StudentService.java` `register` after the class record is saved and flushed (same transaction). Tests in `BT/fees/FeeClassRecordIT.java`: a student registered in a finalised year has an account at once; in a Draft year none; and a deterministic concurrency test (quickstart 22): hold `PESSIMISTIC_WRITE` on the status row in a test transaction, start a registration in another thread, assert it is blocked (poll `pg_locks` or use a short `lock_timeout`), commit, and assert the new class record has an account; also the reverse order (registration holds the share lock, finalise waits, then includes the record). No sleeps or latches as the proof. (FR-006; SC-002; review-tasks S4)
- [ ] T017 [P] [US2] Profile fees: new `BE/fees/StudentFeesService.java`, `BE/fees/StudentFeesController.java` and the `StudentFees` dto for `GET /api/students/{id}/fees[?yearId=]`: state `FEE_SET`, `FEE_NOT_SET` or `NOT_FINALISED`, yearly fee, registration fee, total fee, due date, the brought-forward amount (the `BroughtForward` entity of T008; zero when none), total due = total fee + brought-forward, and brought-forward alone when the state is not `FEE_SET` (spec FR-009). The year is the year of the record the profile shows (via `EnrollmentLookup`) or `yearId`. 404 for an unknown student. Test `BT/fees/StudentFeesIT.java`: every state, totals with `compareTo`, no receipts or paid or pending fields in the response. (FR-005, FR-009, FR-011)
- [ ] T018 [P] [US2] List columns: in `BE/students/StudentTable.java` add `totalFee` (number, `BigDecimal`: `amount + coalesce(registration_fee, 0)` of the shown record's `fee_accounts` row, null when none; sortable and filterable) and the internal `feeState` (`NOT_SET` when the account exists without an amount), using the same shown-record subquery as the other columns; this is the one approved cross-module read (constitution VII 1.1.0, plan Complexity Tracking) and the guard test of T007 lists it. Export: `totalFee` as a numeric cell (T006). NEEDS-CLARIFICATION: what the export shows for a student whose fee is "Fee not set" or "Not finalised" (blank cell, or a text column `feeState`); default to a blank `totalFee` plus a hidden text column `feeState` until the owner decides. Tests: extend `BT/students/StudentListIT.java` (column values, sort, filter, export cell type) and `BT/students/StudentListPerformanceIT.java` (10,000 students: page, sort and filter under 2 s, export under 30 s; use `FeeTestData`). No paid, pending or fee-status columns. (FR-010; SC-003 of F1)
- [ ] T019 [US2] Front end, profile and list: `FE/features/students/student-profile/student-profile.component.ts` (+ `.html`) gains the Fees block (yearly fee or "Fee not set" or "Fee structure not finalised", registration fee if any, brought forward, total due, due date; no receipts, paid or pending), `FE/features/students/student-list/student-columns.ts` gains the Total fee column ("Fee not set", "Not finalised" or the amount), `FE/features/students/students.service.ts` calls the fees endpoint. Tests: unit tests and `frontend/e2e/fees-student-views.spec.ts` for quickstart scenarios 6, 11, 15, 21 and 23 (28,000.00 displayed with two decimals; the exported cell is numeric). (FR-005, FR-009, FR-010, FR-011)

**Checkpoint**: finalised years show correct fees everywhere; new students get a fee at once.

---

## Phase 5: User Story 3 - Revise a finalised structure (P2)

**Goal**: change a finalised structure one row at a time, with a warning, confirmation and a full recalculation.

**Independent test**: quickstart scenarios 7 to 10.

- [ ] T020 [US3] [PLAN MODE] Revise: complete the confirmed path of `POST/PUT/DELETE` rows for a Finalised year in `BE/fees/FeeStructureService.java` and `BE/fees/FeeStructureController.java` (the no-`confirm` refusal already exists from T012). With `confirm=true`: in one transaction lock the status row, check `structureVersion` (stale first, before the confirmation rule), apply the row change, bump the status version, recompute every class record's account with `FeeSelection` and update only accounts whose values differ, write one audit entry (Create, Edit or Delete on `FeeRow#id`, reason `Revise`, old and new row values, `studentsRecalculated` = every class record of the year including Left students, `accountsChanged`). Receipts are never touched (none exist yet). The "excess paid" rule is F3 and is not built here. Show the transaction in the plan first. Test `BT/fees/FeeReviseIT.java`: scenarios 7 to 10 (delete a row, cancel, confirm, no `confirm`), only changed accounts are written, stale structure version wins over the warning, audit counts, a 2,000-record revise under 10 s (SC-004), a failing audit write leaves rows and accounts unchanged. (FR-003, FR-004, FR-005, FR-006, FR-012; SC-003, SC-004)
- [ ] T021 [US3] Revise on the screen: `FE/features/fees/fee-structure.component.ts` (Revise button, per-row save and delete in revise mode, "year is being revised" notice), new `FE/features/fees/revise-dialog.component.ts` (warning in the existing warning tokens, bg `#FFF3DC` text `#6B4200`; "Every student's fee for the year will be recalculated"; the student count from the 409 `current`; Confirm sends the same request with `confirm=true`; Cancel changes nothing), `FE/features/fees/fees.service.ts`; a stale response reloads the structure before any warning. Tests: unit tests and `frontend/e2e/fees-revise.spec.ts` for scenarios 7 to 10 and 16 (finalise in one window, stale edit in the other). (FR-003, FR-004, FR-013)

---

## Phase 6: User Story 4 - Enter pending fees of earlier years (P2)

**Goal**: one brought-forward amount with a short note per student.

**Independent test**: quickstart scenario 12.

- [ ] T022 [P] [US4] Brought forward, backend: new `BE/fees/BroughtForwardService.java`, `BE/fees/BroughtForwardController.java` and dto records for `PUT /api/students/{id}/brought-forward` (the value is already read by `StudentFeesService` from T017; this task does not edit T017's files): amount "zero or more, two decimals" (422 otherwise), note optional at most 200 characters (422 over 200), a null `version` creates the row, a stale version is `STALE_VERSION` with the latest record, "clear" saves 0 and an empty note and never deletes, student must exist (`students.api.StudentLookup`). Audit (`FeesAudit`): Create the first time, Edit after; old and new amount in full; for the note only `noteChanged: true`, never the text. A save that changes nothing writes no entry. Test `BT/fees/BroughtForwardIT.java`: scenario 12, the 200-character limit, total due includes it, audit has no note text, never changed by finalise or revise. (FR-007, FR-009, FR-011, FR-012)
- [ ] T023 [US4] Brought forward on screen: `FE/features/students/student-form/student-form.component.ts` (+ `.html`), edit mode only: a "Brought forward" section with amount, note (hint "a short reason, at most 200 characters") and its own save button, error messages beside the fields, `FE/features/students/students.service.ts` and the Fees block of `FE/features/students/student-profile/student-profile.component.ts` (shows the amount and note). Tests: unit tests and `frontend/e2e/fees-brought-forward.spec.ts` for scenario 12 (enter 2,500.00, change to 1,000.50, clear; a student with fee not set still shows it). (FR-007, FR-009, FR-011)

---

## Phase 7: User Story 5 - Standard and category cannot change after finalising (P2)

**Goal**: the fee lock, with the F1 form explaining it.

**Independent test**: quickstart scenarios 13 to 15.

- [ ] T024 [US5] [PLAN MODE] Lock, backend: in `BE/students/StudentService.java` `changeEnrollment`, call `ClassRecordFees.isLocked(record.getAcademicYearId())` and, when locked and the standard or category differs from the record's, throw `CodedConflictException` `FEE_STRUCTURE_FINALISED` (409) before any change; a division-only change stays allowed; add `feeLocked` to `BE/students/EnrollmentDto.java` and fill it from the same call (also in the profile's class history). Registration is unaffected. Update every place that constructs `EnrollmentDto`: `BE/students/StudentService.java` and the F1 tests that build or compare it (search `EnrollmentDto` under `BT/students/`). Show the check order (stale version, lock, validation) in the plan first. Test `BT/fees/FeeLockIT.java`: scenarios 13 to 15 (finalised: standard and category refused, division allowed; Draft and no structure: F1 rules), `feeLocked` flag values. (FR-008; SC-005)
- [ ] T025 [US5] Lock on screen: `FE/features/students/class-record-dialog/class-record-dialog.component.ts` shows standard and category as fixed (disabled) with the message "The fee structure for this year is finalised, so the standard and category cannot be changed. The division can." when `feeLocked`, division still editable; a 409 `FEE_STRUCTURE_FINALISED` from the server shows the same message; add `feeLocked` to the `Enrollment` interface in `FE/features/students/students.service.ts`. Tests: unit test and `frontend/e2e/fees-lock.spec.ts` (and keep `frontend/e2e/students-edit.spec.ts` green). (FR-008; SC-005)

---

## Phase 8: Polish and cross-cutting

- [ ] T026 Audit log screen: `FE/features/audit/audit-log.component.ts` area filter gains `Fees` (the actions list already has Finalise); extend `frontend/e2e/audit-log.spec.ts` to open the log after a finalise, a revise and a brought-forward change and check the entries (amounts in full, no note text, reason Revise visible). Do after T021 and T023, because it drives those UI flows (or seed the entries through the API with `frontend/e2e/support/fees.ts`). (FR-012)
- [ ] T027 Coverage and full check: run `scripts/check-fr-coverage.sh --strict` and `scripts/verify.sh`; every FR-001 to FR-014 must be referenced by a test file this branch added or changed against the updated local `main` (T001); FR-014 is carried by `@Covers("FR-014")` on `BT/shared/ModuleDependencyTest.java` (T007). Fix gaps by adding the missing `@Covers` or title, not by weakening the script. Record the counts in `specs/003-fees/quickstart-results.md`. (FR-014; SC-008)
- [ ] T028 Documentation and commands: review `CLAUDE.md` for commands that changed (for example a new test-profile endpoint or a script flag) and update only what changed, else write "no change needed" in `specs/003-fees/quickstart-results.md`; add a line to the Change log of `docs/design/ui-prototype.md` (the fee screen is one row per standard and category with a due date and registration fee, not the drawn grid); update `docs/guides/feature-workflow.md` if it lists the F2 status; note in `docs/decision-log.md` the spike timings from T003 if they changed a decision. (process)
- [ ] T029 Run quickstart scenarios 1 to 19 and 21 to 23 from `specs/003-fees/quickstart.md` against a built app (`scripts/build-frontend.sh`, `./mvnw spring-boot:run`), synthetic data only; for scenarios that need a browser use the Playwright specs of this branch and say which spec covers each; write each scenario's result (pass or fail, with the observed values) in `specs/003-fees/quickstart-results.md`. Note that SC-004 and the list timings were measured on the development PC, not on the 4 GB Windows 10 target (carry to F11). Failures become fix tasks, not silent edits. (all FRs; SC-001 excluded)
- [ ] T030 OWNER: do the timed check of quickstart scenario 20 yourself: enter the fee rows for 12 standards with a due date each and finalise, with a stopwatch, as an admin who has used the screen once; record the time and pass or fail (under 10 minutes) in `specs/003-fees/quickstart-results.md`. (FR-001, FR-002; SC-001)

---

## Dependencies and order

- Phase 1 in order: T001 -> T002 -> T003 -> T004 (the migration is approved by you before anything uses it).
- Phase 2 needs T004. T005 to T010 touch different files and can run in parallel; T008 and T009 are needed by every story.
- US1: T011 -> T012 -> T013 (same service and controller files), then T014 -> T015 (T014 can start after T011 for the service, but both edit `fees.service.ts` and the routes, so keep the order). US2: T016, T017, T018 can run in parallel after T013; T019 after T017 and T018. US3: T020 after T013 (same files as T011 to T013), then T021 after T015. US4: T022 can run in parallel with T020 and T021 after T008 and T017; T023 after T022 and T019. US5: T024 after T016 (same `StudentService.java`), then T025, which also comes after T023 (both edit `students.service.ts` and the profile component).
- Polish: T026 after T021 and T023; T027 after all code tasks; T028 and T029 after T027; T030 last (owner).
- MVP: Phases 1 to 4 (T001 to T019): a finalised structure gives every student a fee, shown on the profile and list.

## Parallel examples

- After T004: T005, T006, T007, T008, T009, T010 together.
- After T013: T016, T017, T018 together.
- After T008 and T017: T022 beside T020 and T021.

## Not decided (NEEDS-CLARIFICATION)

- Export of "Fee not set" and "Not finalised" in the Students Excel (T018): default is a blank `totalFee` and a hidden `feeState` text column until the owner decides.
- "Excess paid" (spec FR-004) is F3 behaviour and has no F2 task; F2's FR-004 tests cover warning, confirmation and recalculation only.
- Nothing else in this task list is undecided; the financial rules come only from the agreement, the decision log and the clarified spec.

## Coverage

| Requirement | Tasks |
|---|---|
| FR-001 rows and validation | T004, T005, T008, T011, T012, T015 |
| FR-002 Draft and finalise | T011, T012, T013, T015 |
| FR-003 no ordinary edit after finalising | T012, T013, T015, T020, T021 |
| FR-004 revise | T005, T020, T021 |
| FR-005 which fee applies | T009, T013, T017, T019, T020 |
| FR-006 fee accounts | T003, T004, T007, T008, T013, T016, T020 |
| FR-007 brought forward | T004, T008, T022, T023 |
| FR-008 lock | T005, T007, T024, T025 |
| FR-009 profile Fees block | T017, T019, T022, T023 |
| FR-010 list Total fee | T006, T018, T019 |
| FR-011 money rules | T004, T006, T008, T009, T012, T017, T019, T022, T023 |
| FR-012 audit | T008, T012, T013, T020, T022, T026 |
| FR-013 screen, stale, no current year | T005, T011, T012, T014, T015, T021 |
| FR-014 every FR tested | T007, T010, T027 |

| Success criterion | Tasks |
|---|---|
| SC-001 12 standards in under 10 minutes | T030 (owner, manual) |
| SC-002 correct fee for every case | T009, T013, T016 |
| SC-003 warning count and recalculation | T020, T021 |
| SC-004 2,000 records under 10 s | T003, T013, T020 |
| SC-005 lock refused and explained | T024, T025 |
| SC-006 two decimals, invalid refused | T004, T006, T012, T019 |
| SC-007 every change audited, none for failures | T008, T013, T020, T022, T026 |
| SC-008 every FR tested | T027 |
