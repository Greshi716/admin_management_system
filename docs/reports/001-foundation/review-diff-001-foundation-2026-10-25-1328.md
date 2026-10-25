Result: FIX FIRST

# Diff review: 001-foundation (whole branch against main)

Date: 2026-10-25 13:28. Scope: `git diff main...HEAD` (115 commits, 259 files) plus the working tree (one uncommitted change to `specs/001-foundation/tasks.md`, which appeared during this review and was not made by the reviewer). Read against `specs/001-foundation/spec.md`, `plan.md`, `tasks.md`, `research.md`, `.specify/memory/constitution.md`, `CLAUDE.md` and `docs/design/ui-tokens.md`.

## Verdict

The feature works and the build is green. Backend 179 tests, frontend 76 unit tests and FR coverage 46 of 46 all pass with JDK 21. The never-do list is respected:
- no money code
- no edited migration from main (main has no migrations)
- no hard deletes of people
- no secrets or real data in tracked files
- master-data writes audit in the same transaction (`Propagation.MANDATORY`)

One constitution violation must be fixed before merge: the `audit` and `shared` modules depend on each other (Principle VII says module dependencies must not form a cycle). Several should-fix items affect how much the FR-025 masking guarantee and the FR-036/SC-009 "every FR has a passing test" claim can be trusted.

Counts: 1 blocking, 8 should fix, 13 minor.

## Blocking

**B1. Module cycle between `audit` and `shared` (constitution VII: module dependencies must not form a cycle; violations block merge)**
- Location: `management/src/main/java/com/admin/management/shared/table/TableEndpoints.java:12-13` (imports `audit.AuditService`, `audit.SensitiveMasker`); `shared/table/TableQueryService.java:17`; `shared/excel/ExcelExporter.java:22`. In the other direction: `audit/AuditColumns.java:5-6`, `audit/AuditController.java:10-11` and `audit/AuditLogRequest.java:9-13` import `shared.table`, `shared.time` and `shared.web`.
- Problem: `shared` uses `audit` and `audit` uses `shared`. The plan lists both as modules (plan.md Constitution Check VII). Its claim that "other modules use only public API types" holds, but the dependency graph has a cycle.
- Fix: move `SensitiveMasker` into `shared` (for example `shared/masking`), and have `TableEndpoints` call a small `ExportRecorder` interface declared in `shared`, implemented by `audit`.

## Should fix

**S1. `AuditService` never masks sensitive fields itself, and the masking test cannot fail**
- Location: `audit/AuditService.java:58-61`; `audit/AuditMaskingIT.java:43-44`
- Problem: T019 says AuditService "applies SensitiveMasker", and FR-025 requires that entries hold only masked values. `save` only drops `password`/`passwordHash`; masking any other sensitive field is left to each caller. `AuditMaskingIT` masks the value itself before calling `record`, so it only proves that a masked string stays masked. If a later module forgets to mask, it will log a full Aadhaar number and no test will notice.
- Fix: let callers declare sensitive keys (or keep a registry in the audit module), mask them inside `AuditService`, and change the test to pass the raw value.

**S2. Password and hash fields are filtered only at the top level of the changes map**
- Location: `audit/SensitiveMasker.java:37-46`; `masterdata/MasterDataAudit.java:28,41,46`
- Problem: master-data entries nest values under `old`/`new`. `withoutNeverStored` looks only at top-level keys, so a `password` or `passwordHash` inside `new` would be stored. This breaks FR-025 ("MUST NOT contain the password or password hash in any form") as soon as any snapshot contains such a field.
- Fix: walk nested maps and lists recursively, and add a test with a nested `password`.

**S3. An export can duplicate or skip rows when data changes during it**
- Location: `shared/table/TableQueryService.java:73-86` (`stream`, OFFSET chunks of 500 under `@Transactional(readOnly = true)`, which is READ COMMITTED on PostgreSQL); `audit/AuditColumns.java:28` (audit log sorted by `at` descending)
- Problem: each chunk query takes a new snapshot. Any audit row written while the audit log is exported, for example a sign-in from another tab, goes to the top of the order and shifts every later offset by one. A row is then repeated at a chunk boundary. SC-007 requires "exactly the rows".
- Fix: use `isolation = Isolation.REPEATABLE_READ` on `stream`, so all chunks see one snapshot, or switch to keyset paging.

**S4. The audit module reads the security module's `users` table directly (constitution VII: "direct access to another module's internals or tables is prohibited")**
- Location: `audit/AuditService.java:68-70` (`select id from users where username = ?`); `audit/AuditEntry.java:59` (`@Formula("(select u.username from users u ...)")`)
- Problem: this crosses a module boundary at the table level.
- Fix: add a `CurrentUserProvider` interface in `shared` that `security` implements. Either store the username on the audit row when it is written, or document the foreign key and the read-only join as an accepted exception in the decision log.

**S5. `verify.sh` does not run Playwright, but four FRs are covered only by Playwright tests**
- Location: `scripts/verify.sh:9-15`; `scripts/check-fr-coverage.sh:19-22`
- Problem: FR-028, FR-041, FR-042 and FR-043 have no backend or unit test; their only tests are e2e specs (`shell.spec.ts`, `look-and-feel.spec.ts`, `theme-conformance.spec.ts`). FR-036 is checked for real only by `e2e/fr-coverage-check.spec.ts`. The coverage script counts e2e test titles but never runs them. So "VERIFY OK, 46 of 46 covered" does not show that those tests pass, which SC-009 and constitution VIII require.
- Fix: add `npm run e2e` to verify, or a documented required gate before push, or have the coverage script report e2e-only FRs separately.

**S6. Wrong FR tags inflate coverage**
- Location: `management/src/test/java/com/admin/management/security/TestSupportIT.java:20` (`@Covers("FR-036")` on a test of the test-support endpoint); `frontend/e2e/academic-years.spec.ts:62` ("FR-013" on an edit-name test, which is FR-010); `frontend/e2e/divisions.spec.ts:42` ("FR-017" on a mark-inactive test, which is FR-020)
- Problem: because of the `TestSupportIT` tag, the backend side of FR-036 counts as covered by a test that does not check FR-036.
- Fix: correct the tags, and give FR-036 a test that `verify` runs, for example a bash or JUnit test of `check-fr-coverage.sh --strict`.

**S7. Task status in `tasks.md` is wrong**
- Location: `specs/001-foundation/tasks.md:257` (T101 is `[X]` but its own text says "NOT COMPLETE"; SC-001, SC-005 and SC-008 have not been checked by a person); `tasks.md:260` (uncommitted working-tree change turns T104 "Open a pull request ... needs the owner's explicit go-ahead" into `[X]`)
- Problem: there is no sign of a pull request. `gh` is not installed here and no `001-foundation-*` tag exists. T104 requires the owner's approval, and no agent message can give that.
- Fix: set T104 back to `[ ]` unless the owner approved it and the PR exists. Mark T101 as partial (`[ ]` with a note) until the timed checks are done.

**S8. Error state of the standard table and of the session check**
- Location: `frontend/src/app/shared/table/campus-table.component.ts:117-127` with `campus-table.component.html:137-143`; `frontend/src/app/core/auth/auth.store.ts:44-47`
- Problem: if the first load fails with anything except 422 (500, or the server is down), the table shows "No rows found / Try a different search". That empty-state message is wrong here; the review checklist requires separate loading, empty and error states. Also, `ensureLoaded` treats any failure of `/api/auth/me` (network error, 500) as "not signed in" and sends the admin to sign-in.
- Fix: add an `error` signal with an inline "Could not load the list. Try again" row. In `ensureLoaded`, map only 401 to signed out.

## Minor

**M1. Sensitive values are masked twice on export.** Location: `TableQueryService.java:132` and `ExcelExporter.java:69-71`. Masking is idempotent, so the result is right, but the logic is duplicated. Fix: mask in one place.

**M2. Dead code.** Location: `TableRequest.forExport()` (`shared/table/TableRequest.java:52`) has no callers. Fix: remove it.

**M3. Sensitive columns leak through search and the export audit entry.** Location: `ColumnDef.asSensitive()` (`ColumnDef.java:83`) keeps the column searchable and filterable on the raw value, so contains/equals queries can probe the full value. The export audit entry stores the global search text unmasked (`TableEndpoints.java:84-86`). No sensitive column exists in F0. Fix: make sensitive columns not searchable by default and mask the stored search text.

**M4. Audit area "Audit log" is not in the spec.** Location: `audit/AuditController.java:22`; `frontend/src/app/features/audit/audit-log.component.ts:13`. The spec's areas are Security, Master data and later Fees, Payroll, Fund (US4, FR-022, FR-027). Fix: record it in `docs/decision-log.md` or use an existing area.

**M5. Credentials-file delete after a password change.** Location: `security/AuthController.java` `changePassword` (`credentials.deleteIfExists()` runs after the commit). An I/O error returns 500 although the password was changed. Fix: catch the error, log a warning that names the path, and return 204.

**M6. Parallel failed sign-ins (unsure).** Location: `security/LoginAttemptService.java:72-85` with `User @Version`. Two failures at the same moment could raise an optimistic-lock error, which `ApiExceptionHandler.optimisticLock` turns into a 409 STALE_VERSION on the sign-in call. This is unlikely with one admin and was not reproduced. Fix: retry once, or update the counter with a single `UPDATE ... SET failed_attempts = failed_attempts + 1`.

**M7. `tasks.md` and `plan.md` still describe PrimeNG.** Location: `tasks.md:39,42,74,76,246` (PrimeNG, `FE/theme/campusflow-preset.ts`, `p-table`, `providePrimeNG`) and the plan.md structure tree (`theme/ campusflow-preset.ts (only place with colours)`). These tasks are marked done, but the file does not exist: PrimeNG was removed (research R2, superseded). T096 names `shell.component.css`, which also does not exist. Fix: update the task and plan text to the Tailwind `@theme` approach.

**M8. Append-only trigger does not cover TRUNCATE.** Location: `V2__users_audit.sql:44-46`. The trigger blocks UPDATE and DELETE but not TRUNCATE. The application cannot reach this. Fix: add a statement-level `BEFORE TRUNCATE` trigger in a new migration (V5). Do not edit V2, which has already been applied to the dev database.

**M9. No length limits on names and codes.** Location: `AcademicYearDto`, `StandardDto`, `DivisionDto`, `StudentCategoryDto`. All are TEXT columns. Fix: add `@Size(max = ...)` with a clear message.

**M10. Test-only code ships in the production jar.** Location: `src/main/resources/application-test.properties` (known password), `security/TestSupportController.java` (unauthenticated admin reset), `shared/config/TestDatabaseReset.java`. All of it is gated by the `test` profile, and `FlywayCleanGuard` stops a clean against a database not ending in `_test`. Activating `test` by mistake on a real database would still expose an unauthenticated reset to a password that is committed in the repo. Fix: refuse to start under `test` unless the datasource name ends in `_test`, or move this code out of the packaged jar.

**M11. Test helper year differs from the spec.** Location: `frontend/e2e/support/test-data.ts` `academicYearAround` and `smoke.spec.ts:18`. The helper builds April-to-March years; the spec's examples use 01-06 to 31-05. The tests do not depend on this, but it can mislead. Fix: align the helper or note why it differs.

**M12. Changes outside the F0 tasks are bundled on this branch.** `.claude/agents` and `.claude/commands` (process tooling), `docs/agreement/Client_Questions_*`, the constitution amendment to 1.0.1 (`.specify/memory/constitution.md:36-38`, which also needs the owner's approval to be recorded per Governance), and edits to the tracked `.idea/*` files despite `.idea/` being in `.gitignore`. Fix: record the owner's approval of the amendment, and untrack `.idea/`.

**M13. `verify.sh` does not check the Java version.** Location: `scripts/verify.sh:7`. On this machine `JAVA_HOME` points to JDK 17, and the first verify run failed with "class file version 65.0". Fix: fail early with a clear message when `java -version` is below 21.

### Checked and clean
- Money: no float or double anywhere. `BigDecimal` is written to Excel as text (`ExcelExporter.java:107-109`).
- Migrations: main has no migrations; V1 to V4 are new on this branch and no V5 was needed.
- Deletes: no hard deletes or cascades on people; the division foreign keys use `ON DELETE RESTRICT`.
- Audit: every master-data create, edit, delete and inactive change goes through `MasterDataAudit` → `AuditService.record` (`MANDATORY`) in the service transaction.
- Secrets: `git ls-files` shows no `.env`, `credentials-first-run.txt`, `backups/`, `real-data/` or `data/`. Bootstrap logs only the file path. `LoginRequest` and `ChangePasswordRequest` hide passwords in `toString`.
- Fixtures: synthetic only (`123456789012`, `987654321098`).
- Security: every `/api/**` path needs authentication except login (and test-support under the test profile only). CSRF uses a cookie token with the SPA handler. Session id changes at sign-in. 401 and 403 answer with JSON. Error bodies never include messages.
- SQL: the Criteria API is used everywhere, unknown sort and filter fields are refused with 422, and LIKE wildcards are escaped.
- Tests: no `@Disabled` and no `test.skip`. `TablePerformanceIT` is tagged `performance` and excluded by default, as T083 intends.
- Frontend: strict TypeScript, no `any`, no hard-coded colours outside the `@theme` block in `styles.css`, buttons use `min-height: var(--spacing-control)` (44 px), and icon-only paging buttons have `aria-label`.
- History rewriting: not checkable from the local repository alone. Nothing suggests it happened.

## Verify result

- `bash scripts/verify.sh` with the machine's default `JAVA_HOME` (JDK 17): FAILED before any test ran ("compiled by a more recent version of the Java Runtime (class file version 65.0)"). This is an environment problem, not a code problem (see M13).
- `bash scripts/verify.sh` with `JAVA_HOME` set to JDK 21.0.11: **VERIFY OK**. Backend 179 tests, 0 failures, 0 errors, 0 skipped. Frontend 11 test files, 76 tests passed. FR coverage 46 of 46 (strict).
- Playwright (`npx playwright test`): **NOT RUN**. Port 8080 was already in use by another process ("http://localhost:8080/api/auth/me is already used"), and the config refuses to reuse a running backend. The reviewer did not stop that process. As a result, the e2e-only FRs (FR-028, FR-041, FR-042, FR-043) and the real FR-036 check were not confirmed in this review (see S5).
