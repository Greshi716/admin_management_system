# Task list review: 003-fees (F2)

Reviewed: `specs/003-fees/tasks.md` against `spec.md`, `plan.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`, `research.md`, the constitution (1.1.0) and `CLAUDE.md`. I also read the merged F1 code and scripts (`scripts/check-fr-coverage.sh`, `scripts/verify.sh`, `StudentTestSupportController`, `AbstractIntegrationTest`, `ApiExceptionHandler`, `frontend/e2e/support/`) and ran `scripts/check-fr-coverage.sh --strict` and `git rev-parse` to test two claims. I did not run the build or the tests.

## Verdict: READY WITH FIXES

Counts: Blocking 2, Should fix 9, Minor 8. Both blocking findings are small edits to `tasks.md` and must be made before T001 and T004 are started; the rest can be fixed before the task they touch.

## Coverage table

Tests are written in the same task as the code (as the owner asked), so the implementation and test columns name the same task unless a separate frontend test exists.

| FR | Implementation task(s) | Test task(s) | Gap |
|---|---|---|---|
| FR-001 rows, validation | T004, T008, T011, T013 | T004 (`FeeSchemaIT`), T005, T011, T013 | T004 test is wrong (B2) |
| FR-002 Draft, finalise | T011, T012, T013 | T011, T012, T013 | none |
| FR-003 no ordinary edit after finalising | T011, T018, T019 | T018 (no `confirm`), T013, T019 | server refusal first tested in T018, not T011 (M2) |
| FR-004 revise | T005, T018, T019 | T005, T018, T019 | none |
| FR-005 which fee applies | T009, T012, T015 | T009, T012, T015, T017 | none |
| FR-006 fee accounts | T012, T014, T018 | T012, T014, T018 | none |
| FR-007 brought forward | T008, T020, T021 | T008, T020, T021 | none |
| FR-008 lock | T005, T022, T023 | T022, T023 | none |
| FR-009 profile Fees block | T015, T017, T020, T021 | T015, T017, T021 | none |
| FR-010 list Total fee | T006, T016, T017 | T006, T016, T017 | export text for "not set" undecided (NEEDS-CLARIFICATION in T016) |
| FR-011 money rules | T004, T006, T008, T011 | T006, T008, T011, T017 | scale-3 rejection cannot be tested in T004 (B2) |
| FR-012 audit | T008, T011, T012, T018, T020 | same, plus T024 | none |
| FR-013 screen, stale, no current year | T011, T013, T019 | T011 (stale), T019 | no test for "no current year blocks the screen" (S6) |
| FR-014 every FR tested | T010, T025 | T025 | T025 wording is vague (S8); check can pass falsely (B1) |

| SC | How verified | Gap |
|---|---|---|
| SC-001 | T028 (owner, manual) | none |
| SC-002 | T009, T012, T014 | none |
| SC-003 | T018, T019 | none |
| SC-004 | T003 spike, T012, T018 | measured on the development machine, not the target PC (M5) |
| SC-005 | T022, T023 | none |
| SC-006 | T006, T011, T017 | T004 part cannot pass (B2) |
| SC-007 | T008, T012, T018, T020, T024 | none |
| SC-008 | T025 | depends on B1 |

## Blocking

**B1. The FR coverage check gives a false pass on this branch, and T001 and T025 rely on it.**
- Tasks: T001, T025 (and the pre-push hook).
- Problem: `scripts/check-fr-coverage.sh --strict` compares test files changed against local `main`. Local `main` is still at `42c1c7f`, while `origin/main` is `ece1410` (F1 merged). I ran the script: it reports "14 of 14 covered" on `003-fees` with no F2 test written, because F1's 27 test files count as "changed" and F1 reuses the ids FR-001 to FR-019. T001 would record a meaningless green baseline, and T025 and the push hook would pass even if an F2 requirement had no test. Once local `main` is updated the same check will correctly be red until the F2 tests exist, so T001's "verify is green" can no longer be true either.
- Fix: add to T001 a step `git fetch` and `git branch -f main origin/main` (or `git pull --ff-only` on `main`, as the git workflow rule says), and change T001's pass condition to "all tests green; the FR coverage line is expected to be red for FR-001 to FR-014 until T025".

**B2. T004's schema test asks the database to refuse something it will not refuse.**
- Task: T004 (`BT/fees/FeeSchemaIT.java`, "three-decimal amount").
- Problem: PostgreSQL rounds a three-decimal value to fit `numeric(12,2)` on insert; it does not raise an error (it only fails on overflow). I am confident of this behaviour; verify with one insert. The rule "more than two decimals is refused" therefore lives only in the service (T011), and the schema test as written can never pass.
- Fix: in T004 drop the three-decimal case (keep negative amount, 201-character note, duplicates, wrong status, overflow) and move the three-decimal refusal test to T011.

## Should fix

**S1. Missing helper for the browser tests.** Tasks T013, T017, T019, T021, T023 all need seeded years, standards, students and fee rows, but no task creates `frontend/e2e/support/fees.ts` (F1 has `support/students.ts`, `support/master-data.ts`). T010 builds only the backend endpoint. Fix: add the helper to T010, and list it in the files of each e2e task.

**S2. T011 says "refused with the F0 no-current-year rule" but that rule is not on the server.** In F0 and F1 the block is the front-end `currentYearGuard`; only `StudentService.register` checks the year on the server. Fix: say in T011 that no server rule is added (the fee API works for any year id) and in T013 that the route uses `currentYearGuard`.

**S3. T024 is marked `[P]` "any time after T012" but its test needs a revise and a brought-forward change in the UI.** Those flows exist only after T019 and T021. Fix: make T024 depend on T019 and T021, or seed the entries through the API in the test.

**S4. T014's concurrency test is timing-based.** Two threads and a latch can be flaky. The design is sound (I walked through both orders), but the test should be deterministic. Fix: hold the status row lock in a test transaction, start the registration in another thread, assert it is blocked (for example with a `lock_timeout` or by polling `pg_locks`), then commit and assert the account exists. Confidence in the design: high; in the test as written: low. Also state that the enrollment insert must be flushed before the `FOR SHARE` read.

**S5. T003 uses "temporary tables" through `JdbcTemplate`.** Temp tables belong to one database connection and the pool hands out different ones, so the spike may fail or measure nothing. I am fairly sure of this; verify. Fix: create ordinary scratch tables in the test and drop them at the end, or pin one connection. Also T003 says "export under 2 s", but F1's SC-003 and the plan allow 30 s for export of 10,000 rows; fix the threshold.

**S6. No test for the no-current-year block (quickstart scenario 17, FR-013).** Scenario 17 and the edge case have no task. Fix: add one e2e case to T013 (fee screen blocked when no year covers today).

**S7. T022 changes a record and a TypeScript type but names neither consumer.** Adding `feeLocked` to `BE/students/EnrollmentDto.java` changes every construction site in F1 (`StudentService.toDto`, tests) and the front-end `Enrollment` interface (`FE/features/students/students.service.ts`), which T023 does not list. Fix: list `students.service.ts` in T023 and the F1 tests that construct the dto in T022.

**S8. T025 gives two ways to cover FR-014 and one of them is circular.** "A test in `BT/fees/` that asserts the coverage list" is undefined. F1 used a real reference (`@Covers("FR-016")` on `ModuleDependencyTest`, and the coverage script itself). Fix: name the test that carries `@Covers("FR-014")` (for example the extended `ModuleDependencyTest` or the `FeeSchemaIT`) and keep T025 for the script run only.

**S9. T011 and T013 are too large for one reviewable commit.** T011: five endpoints, validation, stale version, status row, audit, choices and a test file. T013: new screen, setup layout, routes, service, unit tests and e2e. Fix: split T011 into rows (POST, PUT, DELETE, stale, audit) and read side (`GET` structure, `choices`); split T013 into the Setup group and route and the screen. That gives about 30 tasks, as requested.

## Minor

**M1.** T010 says "behind `TestProfileGuard`" but F1's support controller uses `@Profile("test")` on `/api/test-support` (`StudentTestSupportController`). Use the same.

**M2.** FR-003's server refusal (a row change in a Finalised year without confirmation) is first tested in T018, while T011 returns a placeholder 409 for that case. A placeholder in a commit is risky. Fix: have T011 return the final `REVISE_CONFIRMATION_REQUIRED` behaviour for the no-`confirm` case, and T018 add the confirmed path.

**M3.** T020 is `[P]` yet its text also touches T015's response ("the value inside the `StudentFees` response"). If it edits `StudentFeesService`, it conflicts with T015. Fix: say T015 already reads the amount and T020 does not edit T015's files.

**M4.** T007's second test ("scans sources for SQL table names") does not say which tables count as owned by which module. Fix: list the module-to-table map in the task (for example `fee_*` belongs to `fees`, `student*` to `students`, `users` to `security`).

**M5.** SC-004 and the 2 s list goal are measured on the development PC only; the target is a 4 GB Windows 10 machine. Not fixable here; note it in T027 and carry it to F11.

**M6.** T006 changes a shared exporter used by every list. Besides the four tests it names, say to run the whole F0 export suite and the table e2e (`frontend/e2e/table.spec.ts`).

**M7.** Missing small documentation tasks: add a line to the Change log of `docs/design/ui-prototype.md` (the fee screen is rows, not the drawn grid) and update `docs/guides/feature-workflow.md` if it lists F2 status. Fold into T026.

**M8.** T009 includes "a row with amount 0 (RTE-style)". Zero is allowed by the spec, but naming RTE reads like a fee rule for that category. Fix: say "a row with amount 0.00 is a fee of zero, not 'Fee not set'" and drop the category name.

## Other checks

**Order and `[P]`.** Phases are in a valid order: migration (T004) before entities (T008) and before the handler map (T005); shared code (T005 to T010) before stories. The [P] marks on T005 to T010 touch different files, including T007 and T010, which both live in `BE/students/` but in different files. T014, T015, T016 are different files. The only conflicts are the ones above (S3, M3). Tests come with their code in the same task by the owner's rule, so "tests before code" is not separate; say in each task to write the test first.

**Constitution.** I found no task that breaks a principle. Money is `BigDecimal`/`numeric(12,2)` (T004, T008, T009), audit is in the same transaction and tested (T008, T012, T018, T020), nothing deletes a person, the migration is new (V7), fixtures are synthetic, and the one cross-module read (T016) is the approved exception and is guarded by T007.

**Owner dependencies.** T004 needs your approval of the SQL; T016 needs your decision on how the Excel export shows "not set"; T028 is yours; T001 needs local `main` updated (B1). No mockups or installs are needed.

**Missing but optional.** A security review pass (new endpoints are behind the existing session and CSRF rules; no new secrets) and a contract re-check after implementation are not tasks; the finish-feature workflow covers them.

## Recommended first batch

Give these to `/speckit-implement` first (about 8), after fixing B1 and B2:
1. **T001** (with the `main` update).
2. **T002**.
3. **T003** (after S5).
4. **T004** in plan mode: show the SQL, wait for approval.
5. **T005**, **T008** and **T009** together (T009 in plan mode; different files).
6. **T006** and **T007** next (different files; T006 touches the shared exporter, so review it alone).

Do in plan mode: T004 (migration), T009 (fee selection rule), T012 (finalise), T018 (revise), T022 (lock), as the task list already marks. I would also run T016 (the cross-module list column) with a short plan check because it is the approved exception.
