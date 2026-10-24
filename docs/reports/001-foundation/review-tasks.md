# Task list review: 001-foundation (F0 Foundation)

Reviewed: 2026-10-24, branch `001-foundation`. Files read after they changed on disk: `tasks.md` (104 tasks; T012 and T013 already ticked), `spec.md` (46 FRs, 11 SCs; current year is now derived from today's date, no "make current", no inactive years), `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/openapi.yaml`, `docs/design/ui-tokens.md`, `docs/decision-log.md`, `.specify/memory/constitution.md` (v1.0.1), `CLAUDE.md`. I did not diff the agreement file, so I have not confirmed T012's edits in detail (the decision log does contain the matching entries). Nothing was run or edited except this report.

## Verdict: READY WITH FIXES

The task list is well organised and covers every FR. It follows the current spec decisions (derived current year, no inactive years) consistently. It is not ready to hand to `/speckit-implement` as written because five things would stop or break the first batch (B1 to B5). They are small to fix. Most of the remaining findings are implementation traps that are cheaper to write into the task text now than to find in test failures.

Counts: **5 Blocking, 14 Should fix, 9 Minor.**

## Coverage table

"Impl" = task(s) that build it. "Test" = task(s) that prove it. Gaps are marked **GAP**; partial coverage is explained in the findings.

| FR | Impl | Test | Note |
|---|---|---|---|
| FR-001 | T039, T041, T045 | T030, T037 | |
| FR-002 | T038, T041, T043 | T030, T034 | No task creates the `PasswordEncoder` bean (S11) |
| FR-003 | T043 | T034 | |
| FR-004 | T040, T041, T046 | T030, T037 | |
| FR-005 | T041 | T030 | |
| FR-006 | T041 | T031, T037 | |
| FR-007 | T039, T045 | T030, T037 | |
| FR-008 | T003, T044, T049 | T033 | Test approach is flawed (B4, S4) |
| FR-009 | T041, T043, T048, T092 | T034, T036, T089 | |
| FR-010 | T057, T060, T062, T063 | T050, T056 | |
| FR-011 | T014, T016, T060 | T015, T050, T056 | Message must name the other year, no task does it (S2) |
| FR-012 | T057, T060, T063 | T050, T056, T089 | Needs a `Clock` bean and a time-control approach (B4) |
| FR-013 | T060, T093 | T050, T089 | |
| FR-014 | T058, T061, T062, T064 | T051, T056 | |
| FR-015 | T014, T058, T061 | T051 | |
| FR-016 | T059, T061, T064 | T052, T056 | |
| FR-017 | T014, T061 | T015, T052 | |
| FR-018 | T014 | T015, T066, T067 | |
| FR-019 | T068, T069, T070 | T066, T067 | |
| FR-020 | T060, T061, T069, T063 | T050, T053, T066, T056 | Category and division "in use" cannot occur in F0 (B3) |
| FR-021 | T060, T061, T069 | T055, T066, T076 | T055 uses categories before they exist (B3) |
| FR-022 | T019, T040, T044, T023, T077 | T032, T033, T036, T082, T076 | |
| FR-023 | T019 | T055, T073 | |
| FR-024 | T014 | T015, T072 | |
| FR-025 | T017, T019, T040 | T017, T036, T074 | |
| FR-026 | T077, T078, T079 | T071, T076 | |
| FR-027 | T019, T014 | T075 | |
| FR-028 | T091, T093 | T089 | |
| FR-029 | T020, T021, T027 | T081, T084, T085 | |
| FR-030 | T021, T027, T086 | T081, T084, T085 | |
| FR-031 | T021, T027 | T081, T085 | Derived "current" column cannot be filtered in SQL (S3) |
| FR-032 | T021, T027 | T081, T085 | Same (S3) |
| FR-033 | T022, T023, T027 | T082, T085 | |
| FR-034 | T020 to T023 | T081 | |
| FR-035 | T021, T088, T087 | T083 | |
| FR-036 | T010, T011, T099 | **GAP** (S14) | The check script lists FR-036 itself as uncovered unless a test cites it |
| FR-037 | T025, T091 | T089 | |
| FR-038 | T027, T086, T094 | T090 | T084 does not cite it |
| FR-039 | T026, T095 | T090 | |
| FR-040 | T028, T095 | T090 | |
| FR-041 | T096 | T090 | |
| FR-042 | T009 | T090 | |
| FR-043 | T008, T009, T097 | T090 | |
| FR-044 | T040 | T032, T037 | Pause expiry needs the `Clock` (B4) |
| FR-045 | T042, T043, T047 | T034, T035, T037 | |
| FR-046 | T016, T057 to T059, T063 | T054, T056, T066 | `@Version` pitfall, `current` record (S1); T064/T070 pages omit the stale dialog (M3) |

Success criteria:

| SC | How verified | Gap |
|---|---|---|
| SC-001 | Manual, quickstart 2 (T101) | Manual only, acceptable |
| SC-002 | T030, T037 | |
| SC-003 | T050 | Needs fixed clock (B4) |
| SC-004 | T055, T032, T033, T082 | |
| SC-005 | T071 (filter correctness); 30-second claim is manual (quickstart 10) | Acceptable |
| SC-006, SC-007 | T083 | Timing assertions may be flaky and machine-dependent (S12) |
| SC-008 | **No task or quickstart scenario** | Add a manual timed step (M1) |
| SC-009 | T099 checks references; T100 runs the tests | OK |
| SC-010, SC-011 | T032, T033 | T033 as written cannot work (B4) |

## Blocking

**B1. The FR coverage check would fail `verify` from the first commit.** Tasks T010 and T011.
T010 exits non-zero on any uncovered FR and T011 wires it into `scripts/verify.*`. Until about T099 nearly every FR is uncovered, so `scripts/verify.ps1` fails for the whole feature, and CLAUDE.md says to commit after every task. Fix: make the check report-only by default and strict with a flag (`-Strict`); turn on strict in T099/T100. Also remember T011 changes the scripts the owner relies on.

**B2. US1 depends on code that only exists in US2.** Tasks T041 and T060.
`GET /api/auth/me` returns `currentAcademicYear` (T041), which needs `AcademicYearService.current()` from T060. US1's checkpoint says it works on its own with an empty shell. It also makes `security` depend on `masterdata`. Fix: in T041 return `currentAcademicYear: null` for now and add the lookup in T093 or a small US2 task that adds the field; or move `Me` enrichment into US6.

**B3. Some US2 tests cannot pass at the US2 checkpoint.** Tasks T053, T055, T066, T069.
T053 asserts "in use" for a referenced category and division, and T055 asserts audit entries for categories, but categories exist only after T068/T069 (US3). In F0 nothing references a category or a division, so a 409 "in use" can only be produced for academic years and standards (referenced by divisions). Fix: limit T053 to year and standard; move category assertions to T066; if the "in use" rule is to be tested for division and category, add a test-only referencing table, otherwise note it as untestable until a later feature (confidence: high).

**B4. The test setup in T007 cannot support several tests as written.** Tasks T007, T029, T032, T033, T043, T044, T050, T056, T089.
- T007 uses `@AutoConfigureMockMvc` only. MockMvc has no servlet container, so no `server.servlet.session.timeout`, and `HttpSessionListener` callbacks are not fired for `MockHttpSession` (confidence: high for MockMvc; verify). T033 and T044 (idle expiry audit) need a real server (`webEnvironment = RANDOM_PORT`) or a direct call to the listener.
- T007 cleans the database before each class, after the context has started, so the admin created by `FirstRunBootstrap` (T043) is wiped. No task creates a known test admin for the integration tests (T029 only covers Playwright).
- No task creates a `java.time.Clock` bean (zone Asia/Kolkata). T050, T056 and T089 test "today inside or outside a year", and T032 needs the 5-minute pause to expire. Playwright cannot change server time, so the e2e plan must create years relative to the real today or use a test-profile fixed clock.
Fix: add a Phase 2 task for test infrastructure: a `Clock` bean plus a test override, a `TestAdmin` fixture used after clean, `RANDOM_PORT` base class for the session tests, and a rule for which tests use which.

**B5. Owner actions and decisions are not listed as prerequisites.** Tasks T001, T004 to T009, T025 to T029, T043, T036.
- Node is 22.22.2 on this machine and Angular 22 needs 22.22.3 or newer. T001 only says "verify". Until the owner upgrades Node, T004 to T009, T025 to T029 and every frontend task are blocked.
- The test database `admin_management_test` must exist (T003, T007 need it). T001 does not say who creates it.
- Password rules are not in the spec: no minimum length or policy for `newPassword` (T036, T041, T042). This is a gap, not something to guess. Mark NEEDS-CLARIFICATION with the owner or agree a default and record it in the decision log.
- The "application data directory" for `credentials-first-run.txt` (T043) has no defined path or property name; the decision log defers this to F11 but F0 needs one. Decide a property (e.g. `campusflow.data-dir`) and a default.

## Should fix

**S1. Optimistic locking will not trigger as designed.** Tasks T016, T057 to T059, T060, T054.
If a service loads the entity inside a transaction and copies DTO fields onto it, Hibernate compares the loaded version with the database, which is always equal, so a stale `version` sent by the client is ignored (confidence: high; a very common mistake). Fix: in each update, compare `dto.version` with `entity.version` and throw explicitly, or set the version on a detached entity before merge. Also T016's generic handler cannot return "the latest record" in `current` because it does not know the entity; make the service attach it.

**S2. The overlap message must name the other year.** Tasks T016, T060, T050.
US2 scenario 7 says the message names the overlapping year, but a PostgreSQL exclusion violation (SQLSTATE 23P01) only gives the constraint name. Fix: add a pre-check query for the overlapping year in `AcademicYearService` (keep the constraint as the safety net), and have T016 map 23P01, 23505 (unique) and 23503 (foreign key) separately by constraint name.

**S3. A derived "current" column can't be filtered or sorted like a stored column.** Tasks T057, T021, T078, T063.
FR-031 and FR-032 say every column filters and sorts across the whole result. The `current` flag is computed from today's date, so a plain JPA attribute path does not exist. Fix: define it in `ColumnDef` as an SQL expression (`start_date <= :today AND end_date >= :today`) or explicitly exclude it from filter/sort and say so in the spec. Check the same for joined display columns on divisions (`academicYearName`, `standardName`, T059): `ColumnDef` needs a join path.

**S4. Audit calls that have no transaction will fail.** Tasks T019, T023, T044.
`AuditService.record` is `MANDATORY` (needs an existing transaction). The export endpoint streams a response and has no transaction (T023), and the session listener runs on a container thread with no transaction (T044). Both would throw. Fix: use `recordIndependent` for exports and for session events (or open a transaction explicitly), and decide whether the Export entry is written before streaming starts or after it ends; an entry written first can claim an export that failed.

**S5. A foreign-key violation on delete surfaces at commit.** Tasks T016, T060, T061, T069, T053.
`repository.delete(x)` defers the SQL to flush. The violation then appears at commit, after the service method has returned, so a try/catch inside the service never sees it, and the audit insert in the same transaction rolls back (which is correct) but the error reaches the handler as a transaction exception (confidence: medium; verify). Fix: use `deleteAndFlush` inside the try block and map the exception there, or map `DataIntegrityViolationException` plus `TransactionSystemException` in the handler.

**S6. CSRF cookie priming.** Tasks T039, T025, T037.
With Spring Security's deferred CSRF token, the `XSRF-TOKEN` cookie is only written when something reads the token, so the first `POST /api/auth/login` from Angular can fail with 403 (confidence: medium; verify against Spring Security in Boot 4.1). Fix: add to T039 a filter or request that forces the token to load (e.g. on `GET /api/auth/me`), and make T037 include a login as the very first request.

**S7. `jsonb` mapping under `ddl-auto=validate`.** Tasks T014, T018, T078.
`audit_log.changes` is `jsonb`. A plain `String` attribute fails schema validation, and searching it needs a cast (T078 notes only the search side). Fix: T018 specifies the Hibernate JSON mapping (`@JdbcTypeCode(SqlTypes.JSON)`) and T078 the text cast for search (confidence: medium-high).

**S8. Overlapping scope between tasks.** Tasks T027 and T086; T026 and T049; T021/T022 and T088.
T027 already includes skeleton rows and the empty state, then T086 adds them again; T026 already redirects to sign-in on 401, and T049 does the same ("on a 401 after inactivity"); T088 repeats the chunked streaming already in T021/T022. Fix: remove the duplicates or turn them into clearly different checks (e.g. T088 becomes a measured heap test).

**S9. Wrong `[P]` marks and hidden dependencies.** Tasks T004/T005/T009; T022; T025; T029; T045/T046; T065/T070.
T005 installs into the project T004 creates; T009 needs Tailwind from T005; T022 needs `ColumnDef` from T020; T025 uses the interceptors of T026 (both `[P]`); T029 uses a test bootstrap property that T043 implements; T046 uses the store from T045; T070 edits `masterdata.routes.ts` that T065 creates. Fix: drop `[P]` on those or add "after T0xx".

**S10. Tasks too large for one reviewable commit.**
- T014: six tables, constraints, trigger and seed in one migration. Applied migrations cannot be edited (constitution V), so a mistake costs a new migration. Split into `V2__users_audit.sql` (users, audit_log, trigger), `V3__master_data.sql` (years, standards, divisions), `V4__seed_categories.sql`, and review before the first run. This is schema work: plan mode.
- T021: filters, search, sort, paging and chunked streaming. Split: query building vs streaming read.
- T027: the whole table component. Split: base table + paging, then filters/sort, then search + export.
- T041: four endpoints. Split change-password from login/logout/me.
- T062: three controllers. One task each.
- T056 and T089/T090: several spec files per task.
- T094, T095, T097: touch every page; turn into per-page checklist items done as each page is built, with T097 only a final grep for hard-coded colours.

**S11. Missing tasks.**
- `PasswordEncoder` bean and password rules (B5).
- `Clock` bean and a time-control strategy (B4).
- Data directory property (B5).
- A helper that builds old/new "changes" maps from an entity, so each service does not reinvent FR-021 diffs; without it, audit content will differ per service.
- A guard so Flyway clean can only run against a database whose name ends in `_test` (T003, T007). A misconfigured test profile would wipe `admin_management_db`.
- An architecture test (e.g. ArchUnit) enforcing constitution Principle VII (public API package only, no cycles); no task verifies it.
- Date display `dd-MM-yyyy` (plan R15) has no task.
- No CI task, although `scripts/verify.*` says it is used by CI.

**S12. Performance tests may be flaky.** Task T083.
Timing assertions inside the normal test run depend on the machine. SC-006 and SC-007 are about "the school's normal computer". Fix: tag T083 as a separate (non-default) test run with generous limits, and record the reference machine in the report.

**S13. Idle sign-out behaviour of the frontend is vague.** Tasks T049, T026.
Any API call extends the session, so a polling component (e.g. refreshing the user) would stop the 10-minute timeout from ever firing. Say in T049 that no background request is allowed, and what the user sees at expiry.

**S14. FR-036 cannot pass its own check.** Tasks T010, T099.
The script requires a test reference for every `FR-xxx` in the spec, including FR-036. Fix: add a small test of the script itself that cites FR-036, and note that T100 (not T010) establishes that tests pass (SC-009).

## Minor

**M1.** SC-008 (admin adds a year, divisions and a category in under 10 minutes unaided) has no verification step; add a timed manual step to quickstart (T101).

**M2.** `research.md` R16 still says "Export the mockups to `docs/design/` before screen tasks", and `data-model.md` "Open point" still says to record `affects_fees` as NEEDS-CLARIFICATION. Both are overtaken by the decision log; update them so tasks are not read against stale notes.

**M3.** T064 and T070 (standards, divisions, categories pages) do not mention the stale-version dialog that T063 has for years (FR-046); say so, or make it a shared component.

**M4.** The school name shown in the header (T091) is not defined as a constant or setting. CLAUDE.md names "Birlasagar Higher Secondary School, Porbandar"; say where the string comes from.

**M5.** The Angular unit test runner is not specified (T011 runs `npm test`, T084 writes `.spec.ts`); newer Angular CLI versions default to Vitest rather than Karma. Verify at T004 and write the runner into T011/T084.

**M6.** Module names (`masterdata`, `security`, `shared`) differ from the constitution's list (e.g. "admin setup"). Harmless, but say in plan.md that they map to it.

**M7.** T104 opens a pull request, which is outward-facing. Treat it as a task that needs the owner's explicit go-ahead, not one for `/speckit-implement` to run on its own.

**M8.** T012 and T013 are ticked in `tasks.md`; the Dependencies section also says T013 is "not needed". Fine, but the `tasks.md` summary line "104 tasks" now includes two done items.

**M9.** T070 edits `masterdata.routes.ts` created by T065 in a story that could run in parallel with US2; note the shared file.

## Recommended first batch

Give `/speckit-implement` these first (all backend or tooling, none need Node):

1. **T001**, with the owner doing the installs (Node upgrade, create `admin_management_test`); this is a manual task.
2. **T002** (POI dependency).
3. **T003** (properties, with the guard against cleaning a non-test database).
4. **T006** (.gitignore).
5. **T007**, after revising it per B4 (Clock bean, test admin fixture, `RANDOM_PORT` base class).
6. **T010**, then **T011** only after B1 is fixed (report-only mode).

Do these next, in plan mode as CLAUDE.md requires for schema and auth: **T014** (after splitting it, S10), then **T019**/**T017**/**T018** (audit), and later the auth set **T039 to T044**.

Do not start the frontend tasks (T004 onward) until Node is upgraded (B5).
