# Task list review: 002-students

Reviewed: `specs/002-students/tasks.md` (28 tasks), spec.md (FR-001 to FR-019, SC-001 to SC-010), plan.md, data-model.md, quickstart.md, contracts/openapi.yaml, constitution 1.0.1, CLAUDE.md. I also looked at the F0 code the tasks rely on: the imports between modules, `CampusTable` inputs, the existing e2e specs and scripts, and the F0 test folders.
Date: 2026-10-25. No file changed except this report.

## Verdict: READY WITH FIXES

The list is complete, ordered sensibly and sized close to the owner's brief (about 30 tasks, tests with the code, one shell task). No finding blocks starting. Eight should-fix items are corrections to task text; S1, S2 and S3 should be made before the first batch is given to `/speckit-implement`.

Counts: Blocking 0, Should fix 8, Minor 7.

## Coverage table

| FR | Implementation | Test |
|---|---|---|
| 001 | T003, T008, T010, T012, T014 | T003, T010, T012, T015 |
| 002 | T003, T010, T021 | T010, T015, T021, T023 |
| 003 | T010, T014 | T010, T015 |
| 004 | T021, T022 | T021, T023 |
| 005 | T011, T013 | T011, T013, T015 |
| 006 | T004, T010, T021, T022 | T004, T010, T021, T023 |
| 007 | T017, T019 | T017, T020 |
| 008 | T009, T016, T018 | T009, T016, T020, T025 |
| 009 | T017, T019, T022 | T017, T019, T020, T023 |
| 010 | T005, T008, T012, T016, T017 | T005, T012, T016, T017, T020 |
| 011 | T014 | T014 (unit), T015 |
| 012 | T005, T006, T008, T010, T021 | T005, T006, T008, T010, T021 |
| 013 | T024 | T024 (`students-shell.spec.ts`) |
| 014 | T024 | T024 (updated F0 specs) |
| 015 | T018, T019, T024 | T018, T019, T020, T024 |
| 016 | T007, T027 | T027 (coverage script) |
| 017 | T010, T012, T014 | T010, T015 |
| 018 | T021, T022 | T021, T023 |
| 019 | T006, T017, T019 | T006, T017, T020 |

Every FR has an implementation task and a test task. Success criteria: SC-001 T015 and T025 (timed, manual); SC-002 T010, T015; SC-003 T009, T025; SC-004 T016, T017, T020; SC-005 T010, T021 (and T008, see S2); SC-006 T025 (manual only); SC-007 T024; SC-008 T027; SC-009 T021, T023; SC-010 T017. SC-001 and SC-006 are checked by a timed human run, not by an automatic test (stated in T025).

## Blocking

None.

## Should fix

**S1. T016, T017, T012 and T021 all edit `StudentController.java`, and the parallel example pairs T016 with T017.**
Tasks: T012, T016, T017, T021; "Parallel examples".
Problem: same-file edits in parallel will conflict; T016 also says "or `StudentListController.java`", leaving the file undecided.
Fix: Name the files up front (for example `StudentController` for write endpoints, `StudentListController` for search and export, `StudentProfileController` for profile and history) and remove T016 + T017 from the parallel example or keep them in separate files.

**S2. T008's test cannot pass as written.**
Task: T008 ("a save without an audit entry fails (MANDATORY transaction)").
Problem: a plain repository save has no link to the audit service, so nothing makes it fail; only `AuditService.record` requires a transaction. I am sure of this from `AuditService` (propagation MANDATORY on `record` only).
Fix: Replace with "when `StudentAudit` fails inside the registration transaction, the student row is rolled back" (move it to T010) and test `StudentAudit` writing entries with the right area, entity and masking.

**S3. T018 needs changes to the shared table, which no task states.**
Task: T018; `FE/shared/table/campus-table.component.*`.
Problem: `CampusTable` offers an `extraParams` input and a `rowActions` template, but no custom cell template (avatar column, small year text) and I found no dropdown filter by id for Standard, Division and Category. Changing a shared component used by every F0 list is a separate concern and has its own tests.
Fix: Add the component change to T018 (or a separate task before it) with a test, and name the files; check first how filter dropdowns work (unsure).

**S4. Spring Data repositories expose delete methods.**
Task: T008 ("no delete method for students, enrollments or GR numbers").
Problem: an interface extending `JpaRepository` or `CrudRepository` inherits `delete`, `deleteById`, `deleteAll`; the task does not say how to keep them out (constitution IV).
Fix: Say the repositories extend `Repository<T, ID>` and declare only the needed methods; add a reflection test that no `delete*` method exists on the student repositories.

**S5. T016 hides required work in the Notes.**
Tasks: T016; Notes (Excel formula cells).
Problem: neutralising cells that start with `=`, `+`, `-` or `@` is only in the Notes, with "if the F0 exporter does not". I did not check the F0 exporter (unsure).
Fix: Make it a step of T016: check `BE/shared/excel/ExcelExporter.java`, fix if needed, add a test.

**S6. T024 is large and risky for one commit.**
Task: T024 (shell component, routes, about ten e2e specs, a new spec).
Problem: the owner asked for exactly one shell task, so it cannot be split, but the F0 specs include assertions about the sidebar that disappear by design, which conflicts with "selectors only, never weakening". I am fairly sure `look-and-feel` and `theme-conformance` check the sidebar colour (unverified).
Fix: Add an ordered checklist inside T024 (shell, routes with redirects, F0 specs one by one, new spec, final run) with a commit after each step, and say that assertions about the removed sidebar are replaced by the equivalent top-bar assertion, listed in the commit message.

**S7. No task checks that sensitive values stay out of application logs.**
Tasks: none; constitution VI; spec FR-010.
Problem: the plan says request and response bodies are not logged, but no task verifies logs, error responses or exception messages.
Fix: Add to T012 and T027 a check that the application log produced during the registration and edit tests contains no Aadhaar or religion/caste test value.

**S8. T026 and part of T025 are manual and have no owner.**
Tasks: T025, T026.
Problem: they cannot be done by an LLM alone (timed keyboard run, scenario walk-through).
Fix: Mark them MANUAL (as F0's T001) and say who records the results.

## Minor

**M1.** T010 and T016 each hold several concerns (service, validation, name matches; table definition, export, status scope). Keep one task each as asked, but give each an inner step list so a review can follow it.
**M2.** T003 mixes the migration with schema tests of about 25 constraints; fine as one file group, but T003 will be the longest backend task. Allow a split into "migration" and "schema checks" only if it grows.
**M3.** T012's pick-list parameters `yearId`, `studentId` and `enrollmentYearId` overlap; the dialog needs only the record's year and the student. Simplify to `yearId` plus optional `studentId`.
**M4.** T009 and T025 both load 10,000 students; the second repeats the first with the final code. Reuse the generator and say T025 replaces the spike test (delete the spike).
**M5.** Tests are written together with the code, not first; this is the owner's instruction. Constitution VIII only needs tests to exist; note that failing-test-first is not applied.
**M6.** T011 says the pixel limit is "assumed 25 megapixels" (research R2); fine, but record the choice in the decision log if kept (T028).
**M7.** T001 and T011 depend on the JDK 21 and the two PostgreSQL databases from F0 (and port 8080 free for e2e); add one line to Path Conventions so nothing stops at first run.

## Other checks

- **Order**: migration T003 before entities T008; shared code (T004 to T007) before stories; no [P] marker names tasks that touch the same file (T004 to T007 touch different packages; T011 and T013 are separate from T010 and T012). Only T016 and T017 are a risk (S1).
- **Specificity**: files named for nearly every task; T016 ("or `StudentListController.java`") and T024 (many files) are the loosest.
- **Constitution**: no money (I); no applied migration edited, V6 is new (V); audit in the same transaction in T008, T010, T011, T021 (III, with S2 fixing the test); no hard delete except photo bytes (IV, with S4); synthetic fixtures (VI, T002, T015, T025); module guard T007 (VII). I checked that T007's allowed list matches today's imports: only `AuditService` is imported across modules; `AuditExportRecorder` is listed but not needed (harmless).
- **Owner dependencies**: none that block. A decision may be needed after T009 if the spike fails (fallback in plan.md). Manual runs in T025 and T026.
- **Missing but acceptable**: no CI task (the repo uses `verify.sh` and a push hook); no seed data (none needed).

## Recommended first batch (give to `/speckit-implement`)

1. T001, T002 (setup and test data, quick)
2. T003 (migration and schema checks): do in plan mode; it is a schema change
3. T005, T006, T007 (sensitive rule, audit history, dependency guard; small, independent)
4. T004 (master data lookup)
5. T008, then T009 (entities and the list spike), with S2 and S4 applied first

After that: T010 to T012 (US1 backend). Plan mode also for T005 (sensitive-field handling) and T021 (edits that touch money-adjacent rules: GR reservation and class records), as `CLAUDE.md` requires for schema, auth and sensitive data.
