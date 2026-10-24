---

description: "Task list for F0 Foundation"
---

# Tasks: Foundation (F0)

**Input**: Design documents from `/specs/001-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Included. The constitution (Principle VIII) and FR-036 require at least one automated test per FR. Backend tests carry `@Covers("FR-xxx")`; Playwright test titles start with the FR ids.

**Organization**: Tasks are grouped by user story. Shared plumbing (schema, audit service, table framework, app scaffold) is in Phase 2 because every story uses it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US6, mapping to the user stories in spec.md
- Commit after every task, with the FR id in the message (CLAUDE.md)

## Path Conventions

- Backend main: `management/src/main/java/com/admin/management/` (shortened to `BE/` below: `BE/` means that full path)
- Backend test: `management/src/test/java/com/admin/management/` (shortened to `BT/`)
- Resources: `management/src/main/resources/` ; migrations in `management/src/main/resources/db/migration/`
- Frontend: `frontend/src/app/` (shortened to `FE/`); e2e specs in `frontend/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Tooling, dependencies, repository hygiene, recorded decisions

- [X] T001 MANUAL, owner actions first: upgrade Node to 22.22.3 or newer (22.22.2 is too old for Angular 22; 24 LTS is fine) and create the PostgreSQL database `admin_management_test` (no frontend task can start before Node is upgraded). Then verify and document prerequisites: Node 22.22.3 or newer (24 LTS ok), JDK 21 as `JAVA_HOME`, PostgreSQL databases `admin_management_db` and `admin_management_test` exist; update `specs/001-foundation/quickstart.md` with any difference found
- [X] T002 Add `org.apache.poi:poi-ooxml:5.5.1` to `management/pom.xml`
- [X] T003 [P] Set `management/src/main/resources/application.properties`: `server.servlet.session.timeout=10m`, cookie `http-only=true` and `same-site=lax`, keep `spring.jpa.hibernate.ddl-auto=validate`; create `management/src/main/resources/application-test.properties` pointing at `admin_management_test` with Flyway clean enabled for the test profile only; add a startup guard that refuses to run Flyway clean unless the database name ends in `_test`, so a wrong profile can never wipe `admin_management_db`; add `campusflow.data-dir` (default `./data`) as the application data directory for `credentials-first-run.txt`
- [X] T004 [P] Scaffold the Angular app in `frontend/` (latest stable 22.x, standalone, strict TypeScript, no SSR, CSS) and add `frontend/proxy.conf.json` forwarding `/api` to `http://localhost:8080`
- [X] T005 [P] Install in `frontend/package.json`: `primeng`, `@primeuix/themes`, `tailwindcss` (v4) with PostCSS setup, `@fontsource/plus-jakarta-sans`, `@fontsource/noto-sans-gujarati`, `@playwright/test`; add npm scripts `start`, `build`, `test`, `e2e`
- [X] T006 [P] Update `.gitignore`: `management/src/main/resources/static/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`, `frontend/playwright-report/`, `frontend/test-results/`, `credentials-first-run.txt`
- [ ] T007 [P] Create `BT/support/Covers.java` (repeatable annotation `@Covers("FR-xxx")`) and `BT/support/IntegrationTest.java` (base class: `@SpringBootTest`, `@ActiveProfiles("test")`, `@AutoConfigureMockMvc`, Flyway clean and migrate before each class, then create the known test admin through `BT/support/TestAdmin.java`, which uses `JdbcTemplate` and `BCryptPasswordEncoder` directly (no entity or Spring bean dependency), does nothing while the `users` table does not exist yet, and can be switched off with an `@WithoutTestAdmin` annotation for tests that need an empty `users` table, such as T034 and T035); also `BT/support/ServerIntegrationTest.java` with `webEnvironment = RANDOM_PORT` for tests that need a real servlet container (session expiry in T033 and T044, lock-out timing in T032); MockMvc cannot exercise session expiry or `HttpSessionListener`
- [ ] T008 PrimeNG theme spike: create `FE/theme/campusflow-preset.ts` with `definePreset(Aura, ...)` using the tokens in `docs/design/ui-tokens.md` (primary scale around `#6D4AE0`, surfaces `#F7F5FD`/`#EDE7FB`, text `#1E1833`/`#5A5075`, border `#E6E0F5`, input border `#8E84AD`, radius 10 to 16 px, control height 44 px, light only (dark mode off)); confirm import paths against PrimeNG 22.1.4 and `@primeuix/themes` 3.1.0 and record the result under R2 in `specs/001-foundation/research.md`
- [ ] T009 [P] Create `frontend/src/styles.css`: Tailwind import, `@theme` variables mirroring the preset values, Plus Jakarta Sans with Noto Sans Gujarati fallback (FR-042, FR-043)
- [ ] T010 [P] Create `scripts/check-fr-coverage.ps1` and `scripts/check-fr-coverage.sh`: read every `FR-xxx` from `specs/001-foundation/spec.md`, search `management/src/test` for `@Covers("FR-xxx")` and `frontend/e2e` and `frontend/src` spec files for the id in a test title, print uncovered ids; report-only by default (exit 0) and exit non-zero only with `-Strict` / `--strict`, because almost every FR is uncovered until the end of the feature (FR-036, SC-009)
- [ ] T011 Extend `scripts/verify.ps1` and `scripts/verify.sh`: run the FR coverage check in report-only mode (strict mode is switched on in T099), and when `frontend/package.json` exists run `npm ci`, `npm run build` and `npm test` (the existing frontend step runs only `npm run build`)
- [x] T012 [P] (done; later changes such as removing `recovery_code_hash` are recorded in the decision log and agreement section 11, do not re-run) Record decisions in the `## 11. Decision log` table of `docs/agreement/CampusFlow_Agreement_v1.1_2026-10-24.md`: added `active` flag on master tables; audit `area` column and `Delete` action; users `recovery_code_hash`, `password_changed`, `failed_attempts`, `locked_until`; masked values in the audit log instead of "kept out of logs"; categories seeded with `affects_fees = false` until the school confirms
- [x] T013 [P] Not needed: the owner will not export mockup images. Build the screens from `docs/design/ui-tokens.md`, which describes colours, type, sizes and each screen

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, audit service, shared table framework, error model and frontend scaffold used by all stories

**CRITICAL**: No user story work can begin until this phase is complete

- [ ] T014 PLAN MODE (schema work; applied migrations cannot be edited, constitution V). Create three migrations as three separate steps and commits (T014a V2, T014b V3, T014c V4), reviewing each before it is run (do not touch `V1__baseline.sql`): `V2__users_audit.sql` (users, audit_log, trigger), `V3__master_data.sql` (academic_years, standards, divisions), `V4__categories.sql` (student_categories and its seed). Together they hold these tables, each with `id BIGINT GENERATED ALWAYS AS IDENTITY`, and `version BIGINT NOT NULL DEFAULT 0` on every table except `audit_log` (which has an `id` but no `version`):
  - `users`: `name` text required; `username` text required unique; `password_hash` text required; `role` text required `CHECK (role = 'ADMIN')`; `active` boolean default true; `last_login` timestamptz; `password_changed` boolean default false; `failed_attempts` int default 0; `locked_until` timestamptz
  - `academic_years`: `name` text required unique; `start_date` date required; `end_date` date required `CHECK (end_date > start_date)`; `EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&)`; no `is_current` and no `active` column (the current year is worked out from today's date, FR-012)
  - `standards`: `name` text required unique; `display_order` int required; `stream` text `CHECK (stream IN ('Science','Commerce'))` nullable; `active` boolean default true
  - `divisions`: `academic_year_id` and `standard_id` required FKs with `ON DELETE RESTRICT`; `name` text required; `active` boolean default true; unique `(academic_year_id, standard_id, name)`
  - `student_categories`: `code` text required unique; `name` text required; `affects_fees` boolean required, no default; `active` boolean default true; seed O/S, CEW, W, STAFF, S STAFF, RTE, CASUAL with `affects_fees = false`
  - `audit_log`: `at` timestamptz required default now(); `user_id` nullable FK `users`; `username_attempted` text; `area` text required; `action` text required `CHECK (action IN ('Create','Edit','Delete','Login','Export','Cancel','Finalise','Import'))`; `entity` text required; `changes` jsonb; `reason` text; indexes `(at)` and `(area, action, at)`; trigger that raises an exception on UPDATE and DELETE (FR-024)
- [ ] T015 [P] Write `BT/foundation/SchemaIT.java`: verifies the migration applies on an empty test database, seven seeded categories exist, `end_date <= start_date` is rejected, overlapping years are rejected (FR-011), duplicate division is rejected (FR-017), and `UPDATE`/`DELETE` on `audit_log` raise an error (FR-024); annotate with `@Covers` for FR-011, FR-017, FR-018, FR-024
- [ ] T016 [P] Create `BE/shared/web/ApiExceptionHandler.java` and `BE/shared/web/ApiProblem.java`: map validation errors to HTTP 422 with `fieldErrors`, duplicate/overlap/in-use constraint violations to 409 with a clear `detail`, optimistic lock failures to 409 with `code=STALE_VERSION` (FR-046); the service compares the client's `version` with the loaded entity's version explicitly and throws a `StaleVersionException` carrying the latest record in `current`, because Hibernate never sees a stale version when a service loads the entity and copies DTO fields onto it; map PostgreSQL SQLSTATE 23P01 (overlap), 23505 (unique) and 23503 (foreign key) separately by constraint name, and map `DataIntegrityViolationException` and `TransactionSystemException`, per `contracts/openapi.yaml` `Problem`
- [ ] T017 [P] Create `BE/audit/SensitiveMasker.java`: `mask(String)` keeps the last 4 characters and replaces the rest with `*`; `NEVER_STORE` field set (`password`, `passwordHash`) are dropped from change maps (FR-025); add `BT/audit/SensitiveMaskerTest.java` with `@Covers("FR-025")`
- [ ] T018 Create `BE/audit/AuditEntry.java` (entity for `audit_log`, no setters after creation; map `changes` as jsonb with `@JdbcTypeCode(SqlTypes.JSON)` so `ddl-auto=validate` passes) and `BE/audit/AuditRepository.java`
- [ ] T019 Create `BE/audit/AuditService.java` (public API): `record(area, action, entity, changes, reason)` with `Propagation.MANDATORY` (joins the caller's transaction, FR-023) and `recordIndependent(...)` with `Propagation.REQUIRES_NEW` for sign-in events, session events and exports (these run without a caller transaction); resolves the current user from the security context; applies `SensitiveMasker`
- [ ] T020 [P] Create `BE/shared/table/ColumnDef.java`, `TableRequest.java` (fields `first`, `rows`, `sortField`, `sortOrder`, `globalFilter`, `filters` map of `{value, matchMode}`; allowed `rows` 10, 25, 50, 100, default 25); `ColumnDef` supports an SQL expression or join path as well as a plain attribute, so derived columns (the academic year `current` flag: `start_date <= :today AND end_date >= :today`, where `:today` is a bound `LocalDate` taken from the injected `Clock`, never a SQL date function such as `CURRENT_DATE`, so the database time zone and the test clock cannot disagree) and joined display columns (division `academicYearName`, `standardName`) can be filtered and sorted and `TablePage.java` (`rows`, `total`) per `contracts/openapi.yaml`
- [ ] T021 Create `BE/shared/table/TableQueryService.java`: builds JPA Specifications from a list's `ColumnDef`s (search = OR across searchable columns, column filters = AND with `contains`, `startsWith`, `equals`, `dateRange`, `boolean`), applies sort across the full result, paging in SQL, and a streaming read in chunks for export (FR-029 to FR-032, FR-035)
- [ ] T022 [P] Create `BE/shared/excel/ExcelExporter.java`: `SXSSFWorkbook` with window 500, header row from all declared columns including hidden ones, sensitive columns masked, writes to the response output stream (FR-033)
- [ ] T023 Create `BE/shared/table/TableEndpoints.java`: reusable helper that a controller calls for `search` and `export` so a new list only declares its columns (FR-034); export records an `Export` audit entry via `AuditService.recordIndependent` after the stream completes successfully, so a failed export is not logged as done
- [ ] T024 [P] Create `BE/shared/web/SpaFallbackController.java`: forwards non-`/api` paths that are not static files to `index.html`
- [ ] T025 [P] Configure the Angular app in `FE/app.config.ts`: `providePrimeNG` with `campusflow-preset`, `provideHttpClient` with XSRF support and the interceptors from T026, router with view transitions, animations
- [ ] T026 [P] Create `FE/core/http/error.interceptor.ts` and `FE/core/toast/toast.service.ts`: shows a toast for every failed call, redirects to sign-in on 401, passes 409 `STALE_VERSION` bodies through to the caller (FR-039, FR-046)
- [ ] T027 Create `FE/shared/table/campus-table.component.ts` (+ `.html`, `.spec.ts`): wraps PrimeNG `p-table` in lazy mode, inputs `columns` and `endpoint`, search box with a debounce of about 300 ms, per-column filters, sortable headers, paginator with total and page sizes 10/25/50/100, skeleton rows while loading, empty state, Export to Excel button posting the current request to `{endpoint}/export` (FR-027 to FR-034, FR-038)
- [ ] T028 [P] Create `FE/shared/forms/api-error.ts`: maps `fieldErrors` from a 422 response onto a form's controls (FR-040)
- [ ] T029 [P] Create `frontend/playwright.config.ts` and `frontend/e2e/support/test-data.ts`: starts the backend with the `test` profile and the UI, resets the test database before the run, helper `signInAsTestAdmin()` that uses a known test-only password set by a test-profile bootstrap property

- [ ] T105 Test infrastructure: `BE/shared/time/ClockConfig.java` (a `java.time.Clock` bean, zone Asia/Kolkata) with a test override that sets a fixed or movable clock; a rule in the test base classes of which tests use MockMvc and which use `RANDOM_PORT`; a `PasswordEncoder` bean (BCrypt); Playwright years are created relative to the real today, or use the test-profile clock property

**Checkpoint**: Schema migrates, audit and table frameworks compile, Angular app builds. User stories can now start.

---

## Phase 3: User Story 1 - Admin signs in and out (Priority: P1) MVP

**Goal**: Single admin signs in with a server session, is protected by lock-out and idle timeout, is shown a popup to change the default password, and signs out.

**Independent Test**: On a fresh database start the app, read the generated credentials file, sign in, see the shell, sign out, and confirm every screen asks for sign-in again (quickstart scenarios 1 to 4 and 12).

### Tests for User Story 1

> Write these first and confirm they fail before implementing.

- [ ] T030 [P] [US1] `BT/security/LoginIT.java`: correct login returns `Me` and updates `last_login` (FR-001, FR-005); wrong username and wrong password give the same 401 message (FR-004); inactive user is refused (FR-004); unauthenticated `GET /api/auth/me` and any `/api/**` call return 401 (FR-007); password is stored hashed and never appears in responses or audit rows (FR-002)
- [ ] T031 [P] [US1] `BT/security/LogoutIT.java`: after logout the old session cookie is rejected (FR-006)
- [ ] T032 [P] [US1] `BT/security/LockoutIT.java`: 5 consecutive failures pause sign-in for 5 minutes with `retryAfterSeconds`, a correct password is refused during the pause, the pause is audited, success resets the counter (FR-044, SC-010)
- [ ] T033 [P] [US1] `BT/security/IdleTimeoutIT.java`: an expired session produces exactly one idle sign-out audit entry (FR-008, SC-011); do not rely on a 2-second `server.servlet.session.timeout` because Spring Boot rounds the Tomcat session timeout up to whole minutes (check this); expire the session directly (`setMaxInactiveInterval` and invalidate) and assert the listener's audit entry, and assert the configured timeout is 10 minutes
- [ ] T034 [P] [US1] `BT/security/FirstRunBootstrapIT.java`: empty `users` creates exactly one `ADMIN` with a random password in `credentials-first-run.txt`, `password_changed=false`, secrets absent from logs; second start creates nothing; with `campusflow.reset-admin-password=true` a new random password is written, `password_changed` is false again and a Security audit entry is written (FR-003, FR-009, FR-045)
- [ ] T035 [P] [US1] `BT/security/AdminResetIT.java`: starting with the reset property replaces the password hash, the old password then fails, the new one works, the new password appears only in the credentials file and never in logs or the audit entry (FR-045)
- [ ] T036 [P] [US1] `BT/security/ChangePasswordIT.java`: change succeeds with the right current password, sets `password_changed=true`, audit entry shows "changed" and no value (FR-009, FR-025)
- [ ] T037 [P] [US1] `frontend/e2e/auth.spec.ts`: titles start with FR ids; covers sign-in, wrong password message, lock-out message, logout and back button, direct URL redirect, change-password popup after sign-in with "Later" and "Change now" (FR-001, FR-004, FR-006, FR-007, FR-009, FR-044)

### Implementation for User Story 1

- [ ] T038 [US1] `BE/security/User.java` (entity matching `users`, `@Version`) and `BE/security/UserRepository.java`
- [ ] T039 [US1] `BE/security/SecurityConfig.java`: session-based security, JSON 401 instead of redirects, CSRF with `CookieCsrfTokenRepository.withHttpOnlyFalse()` and the SPA request handler, permit `/api/auth/login`, static files and `index.html`, everything under `/api/**` otherwise authenticated; make sure the `XSRF-TOKEN` cookie is written on the first response (force the deferred token to load, e.g. on `GET /api/auth/me`) so the first login POST does not fail with 403 (FR-001, FR-007)
- [ ] T040 [US1] `BE/security/LoginAttemptService.java`: counts failures, sets `locked_until` after 5 for 5 minutes, resets on success, writes audit via `AuditService.recordIndependent` for success, failure and pause (FR-044); when the attempted username matches no user, store it masked and cut to 50 characters, because people sometimes type their password into the username box (FR-025)
- [ ] T041 [US1] `BE/security/AuthController.java` and DTOs (`LoginRequest`, `Me`, `ChangePasswordRequest`): `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` (includes `passwordChanged`, `currentAcademicYear`, `idleTimeoutSeconds`; `currentAcademicYear` comes from a `CurrentAcademicYearProvider` interface in `shared`, which returns null until T060 supplies the real one, so US1 stands alone), `POST /api/auth/change-password` (new password at least 8 characters and different from the current one, FR-009; after a successful change, delete `credentials-first-run.txt` if it exists) (FR-001 to FR-006, FR-009)
- [ ] T042 [US1] `docs/runbooks/reset-admin-password.md`: the developer procedure for a forgotten password (stop the service, start once with `campusflow.reset-admin-password=true`, read the new password from `credentials-first-run.txt` in the data directory, hand it to the admin, restart without the property; the file is deleted automatically when the admin changes the password, and delete it by hand if it is still there after a reset that was not used); the reset logic itself is in T043 (FR-045)
- [ ] T043 [US1] `BE/security/FirstRunBootstrap.java`: runs at startup when `users` is empty; creates the default Admin (random password, BCrypt hash), writes `credentials-first-run.txt` to the application data directory, logs only the file path; test profile may set a fixed test password via a property; when `campusflow.reset-admin-password=true` it replaces the Admin's password hash with a new random password, writes it to the credentials file, sets `password_changed=false`, resets the failed-attempt counter and writes a Security audit entry (`recordIndependent`, no password in it) (FR-003, FR-009, FR-045)
- [ ] T044 [US1] `BE/security/SessionAuditListener.java`: `HttpSessionListener` that records an idle sign-out audit entry when a session expires and a logout entry on logout (FR-008, FR-022); the entry is written with `AuditService.recordIndependent` (no caller transaction exists); a logout must produce one entry only (mark the session as logged out before invalidating it so the listener skips it), and the listener must read the user from the session's stored security context because no request is active when a session expires
- [ ] T045 [P] [US1] `FE/core/auth/auth.store.ts` (signals: user, passwordChanged, idle timeout) and `FE/core/auth/auth.guard.ts` (FR-007)
- [ ] T046 [P] [US1] `FE/features/auth/sign-in/sign-in.component.ts`: sign-in page per `docs/design/ui-tokens.md`, shows lock-out message and retry time, generic wrong-credentials message (FR-004, FR-044)
- [ ] T047 [P] [US1] `FE/features/auth/change-password/change-password-dialog.component.ts`: change-password form (current, new, confirm; 8 characters minimum) used by the popup (T092) and the user menu (FR-009)
- [x] T048 Merged into T047 (one change-password dialog; no separate work)
- [ ] T049 [US1] `FE/core/auth/idle-watch.service.ts`: on a 401 after inactivity send the user to sign-in with a message (FR-008); the app must make no background or polling requests, because any API call extends the session and would stop the 10-minute timeout from ever firing

**Checkpoint**: User Story 1 works on its own with an empty shell page.

---

## Phase 4: User Story 2 - Academic years, standards and divisions (Priority: P1)

**Goal**: Admin maintains academic years (one current, no overlaps), standards and divisions.

**Independent Test**: Quickstart scenarios 5 to 7 and 9.

### Tests for User Story 2

- [ ] T050 [P] [US2] `BT/masterdata/AcademicYearIT.java`: create/view/edit with required fields and unique name (FR-010); `end_date` not after `start_date` and overlapping years refused with a message naming the other year (FR-011); the current year is the one containing today's date, tested with a fixed test clock set to Asia/Kolkata: first and last day of a year, a date between two years (no current year, no fallback), and never more than one (FR-012, FR-013, SC-003); academic years have no inactive status (FR-020)
- [ ] T051 [P] [US2] `BT/masterdata/StandardIT.java`: required `name` and `display_order`, optional `stream` only Science or Commerce, unique name, default order by `display_order` (FR-014, FR-015)
- [ ] T052 [P] [US2] `BT/masterdata/DivisionIT.java`: links to an existing year and standard only, duplicate `(year, standard, name)` refused, same name allowed in another year or standard (FR-016, FR-017)
- [ ] T053 [P] [US2] `BT/masterdata/MasterDataRemovalIT.java`: deleting a referenced year (by a division) or standard (by a division) returns 409 "in use" (division and category references cannot exist in F0, so those assertions wait for a later feature); marking a standard, division or category inactive hides it from pick-lists but keeps it on existing records; an academic year has no inactive option; deleting an unreferenced record succeeds (FR-020)
- [ ] T054 [P] [US2] `BT/masterdata/MasterDataConflictIT.java`: saving with a stale `version` returns 409 `STALE_VERSION` with the latest record, for year, standard and division (FR-046)
- [ ] T055 [P] [US2] `BT/masterdata/MasterDataAuditIT.java`: create, edit, delete and inactive (standards, divisions) each write one audit entry (category audit is asserted in T066) with area `Master data`, old and new values in the same transaction; a failed change leaves no entry (FR-021, FR-023)
- [ ] T056 [P] [US2] `frontend/e2e/academic-years.spec.ts`, `standards.spec.ts`, `divisions.spec.ts`: titles with FR ids; add year, overlap error, the current-year tag follows today's date, add standards and divisions, delete in use (inactive for standards, divisions and categories), two-window conflict (FR-010 to FR-017, FR-020, FR-046)

### Implementation for User Story 2

- [ ] T057 [P] [US2] `BE/masterdata/academicyear/AcademicYear.java`, `AcademicYearRepository.java`, `AcademicYearDto.java` (`name` required; `startDate` and `endDate` required; `version` required on edit; read-only `current` flag computed from today's date)
- [ ] T058 [P] [US2] `BE/masterdata/standard/Standard.java`, `StandardRepository.java`, `StandardDto.java` (`displayOrder` required; `stream` Science or Commerce or null)
- [ ] T059 [P] [US2] `BE/masterdata/division/Division.java`, `DivisionRepository.java`, `DivisionDto.java` (includes `academicYearName`, `standardName` for display)
- [ ] T060 [US2] `BE/masterdata/academicyear/AcademicYearService.java`: create/edit/delete (no inactive option); `current()` returns the year containing today's date using an injected `java.time.Clock` with zone Asia/Kolkata, or none; update compares the client `version` explicitly (S1 of the task review); a pre-check query finds the overlapping year so the message names it, with the constraint as safety net; delete uses `deleteAndFlush` inside the try block and maps the FK violation to the "in use" conflict; also provides the real `CurrentAcademicYearProvider`; every write calls `AuditService.record` (FR-010 to FR-013, FR-020, FR-021)
- [ ] T061 [P] [US2] `BE/masterdata/standard/StandardService.java` and `BE/masterdata/division/DivisionService.java` with the same delete/inactive/audit rules (FR-014 to FR-017, FR-020, FR-021)
- [ ] T062 [US2] Controllers `AcademicYearController.java`, `StandardController.java`, `DivisionController.java` implementing `/api/academic-years`, `/api/standards`, `/api/divisions` including `/search`, `/export`, `/{id}` and, for standards and divisions only, `/{id}/active` (academic years have no `/active` and no `/make-current`) per `contracts/openapi.yaml`, using `TableEndpoints` (FR-034)
- [ ] T063 [P] [US2] `FE/features/masterdata/academic-years/` list page (CampusTable), create/edit form, a "Current" tag on the year that contains today's date (no Make current action and no inactive toggle), delete with in-use message, stale-version dialog showing latest values (FR-010 to FR-013, FR-020, FR-040, FR-046)
- [ ] T064 [P] [US2] `FE/features/masterdata/standards/` and `FE/features/masterdata/divisions/` pages (division form offers only active standards and all academic years, FR-016); both pages use the same stale-version dialog as the years page (FR-046)
- [ ] T065 [US2] `FE/features/masterdata/masterdata.routes.ts` registering the three pages behind the auth guard

**Checkpoint**: Years, standards and divisions fully usable alongside Story 1.

---

## Phase 5: User Story 3 - Student categories (Priority: P2)

**Goal**: Admin maintains categories with the `affects_fees` flag; seven are present after install.

**Independent Test**: Quickstart scenario 8.

### Tests for User Story 3

- [ ] T066 [P] [US3] `BT/masterdata/StudentCategoryIT.java`: seven seeded categories listed (FR-018); create with `code`, `name` and `affects_fees` all required, missing flag refused, duplicate `code` refused (FR-019); edit audited (FR-021); stale version conflict (FR-046); inactive/delete rules (FR-020)
- [ ] T067 [P] [US3] `frontend/e2e/student-categories.spec.ts`: titles with FR ids; seeded list, add, duplicate code error, flag required (FR-018, FR-019)

### Implementation for User Story 3

- [ ] T068 [P] [US3] `BE/masterdata/category/StudentCategory.java`, `StudentCategoryRepository.java`, `StudentCategoryDto.java` (`affectsFees` has no default and must be chosen)
- [ ] T069 [US3] `BE/masterdata/category/StudentCategoryService.java` and `StudentCategoryController.java` for `/api/student-categories` (list, search, export, create, edit, delete, active) with audit (FR-018, FR-019, FR-020, FR-021)
- [ ] T070 [P] [US3] `FE/features/masterdata/categories/` page with an explicit Yes/No choice for "Affects fees" (no pre-selected value), the stale-version dialog (FR-046), and registration in `masterdata.routes.ts` (this file is created by T065; do this task after it)

**Checkpoint**: Categories done; Stories 1 to 3 work independently.

---

## Phase 6: User Story 4 - Audit log (Priority: P2)

**Goal**: Admin reviews and filters the append-only audit log by date range, month, year, area and action.

**Independent Test**: Quickstart scenario 10.

### Tests for User Story 4

- [ ] T071 [P] [US4] `BT/audit/AuditLogQueryIT.java`: filter by date range, month, year, area and action returns only matching rows; `from` after `to` returns 422 (FR-026, SC-005)
- [ ] T072 [P] [US4] `BT/audit/AuditAppendOnlyIT.java`: no HTTP method exists to edit or delete an entry, and the database rejects UPDATE/DELETE (FR-024)
- [ ] T073 [P] [US4] `BT/audit/AuditTransactionIT.java`: a change that rolls back leaves no entry; `AuditService.record` outside a transaction fails (FR-023)
- [ ] T074 [P] [US4] `BT/audit/AuditMaskingIT.java`: a changed field marked sensitive appears masked with the last 4 characters; password and hash never appear (FR-025)
- [ ] T075 [P] [US4] `BT/audit/AuditExtensibilityIT.java`: a test-only module records area `Fees` with action `Cancel` and a reason, and the entry is listed and filterable without any schema change (FR-027)
- [ ] T076 [P] [US4] `frontend/e2e/audit-log.spec.ts`: titles with FR ids; login and edit entries visible, date/month/year/area/action filters, no edit or delete control (FR-021, FR-022, FR-024, FR-026)

### Implementation for User Story 4

- [ ] T077 [US4] `BE/audit/AuditController.java` for `POST /api/audit-log/search` and `POST /api/audit-log/export` using `TableEndpoints`, with extra filters `from`, `to`, `month`, `year`, `area`, `action`; export records an `Export` audit entry (FR-022, FR-026)
- [ ] T078 [US4] `BE/audit/AuditColumns.java`: `ColumnDef`s for time, user, area, action, entity, changes, reason (all searchable and sortable; `changes` shown pre-masked; `changes` is jsonb, so search it through a text cast or a generated text column, not as a plain JPA attribute)
- [ ] T079 [P] [US4] `FE/features/audit/audit-log.component.ts`: CampusTable plus date range, month, year, area and action filter controls; shows old/new values; shows the server's "from is after to" message and no rows when the range is reversed (FR-026)
- [ ] T080 [US4] `FE/features/audit/audit.routes.ts` registering the page behind the auth guard

**Checkpoint**: Audit log visible and filterable.

---

## Phase 7: User Story 5 - Standard table behaviours (Priority: P2)

**Goal**: Prove and finish the reusable table: paging, search, filters, sorting and complete Excel export on every list.

**Independent Test**: Quickstart scenario 11 and performance checks.

### Tests for User Story 5

- [ ] T081 [P] [US5] `BT/shared/TableQueryIT.java` using a test-only entity and `ColumnDef` list: paging returns only one page with the total (FR-029); search matches across searchable columns (FR-030); filters combine with search by AND (FR-031); sort applies across all pages (FR-032); a new list needs only column declarations (FR-034); text containing `%`, `_`, quotes and Gujarati characters is matched as plain text, with LIKE wildcards escaped (spec edge case)
- [ ] T082 [P] [US5] `BT/shared/TableExportIT.java`: export contains all matching rows in sort order and every declared column including hidden ones, sensitive columns masked, empty result gives headings only, an `Export` audit entry is written (FR-033, FR-022)
- [ ] T083 [P] [US5] `BT/shared/TablePerformanceIT.java` with 10,000 synthetic rows: a page, search, filter or sort under 2 seconds and a full export under 30 seconds (FR-035, SC-006, SC-007); tagged as a separate non-default test run with generous limits, and the reference machine is recorded in the report, because timing assertions are machine-dependent
- [ ] T084 [P] [US5] `FE/shared/table/campus-table.component.spec.ts`: debounced search without Enter, sort toggles ascending then descending, empty-state message, page size choices, export button sends the current search, filters and sort (FR-027 to FR-033)
- [ ] T085 [P] [US5] `frontend/e2e/table.spec.ts`: titles with FR ids; on the audit log or a seeded list: next page, search, column filter, sort, export download whose rows match the screen, and the reversed date range message on the audit log (FR-029 to FR-033)

### Implementation for User Story 5

- [ ] T086 [US5] In `FE/shared/table/campus-table.component.ts` (after T027): empty-state text, and Export on an empty result downloads a file with headings only and still writes the audit entry (FR-030, FR-038)
- [ ] T087 [P] [US5] Add indexes needed for the search and sort columns of each list in a new migration `management/src/main/resources/db/migration/V5__table_indexes.sql` only if T083 shows the targets are missed (FR-035)
- [ ] T088 [US5] Ensure the export streams in chunks without loading all rows into memory (check heap use in `TablePerformanceIT`) and that other requests stay responsive during a large export in `BE/shared/table/TableEndpoints.java`

**Checkpoint**: Table behaviour verified on all lists.

---

## Phase 8: User Story 6 - App shell and look and feel (Priority: P3)

**Goal**: Consistent layout, navigation, current-year tag, password popup, and the quality rules for every screen.

**Independent Test**: Quickstart scenarios 2 and 4, plus a visual check against `docs/design/ui-tokens.md`.

### Tests for User Story 6

- [ ] T089 [P] [US6] `frontend/e2e/shell.spec.ts`: titles with FR ids; navigation, user name, current-year tag, Change password and Logout on every screen (FR-028); when today's date is inside no academic year: sign-in lands on Academic years with the create form, other menu items are disabled, a direct link to another screen redirects back, Change password and Logout still work, and creating a year covering today lifts the block (US6 scenario 2, FR-013); popup while the password is unchanged (FR-009); no full page reload on navigation (FR-037)
- [ ] T090 [P] [US6] `frontend/e2e/look-and-feel.spec.ts`: loading state shown on slow responses (FR-038), success/error toast after save (FR-039), inline validation messages while typing (FR-040), layout usable at tablet width 768 px and desktop 1280 px (FR-041), Gujarati sample text renders with the Noto Sans Gujarati font (FR-042), and every screen uses the shared theme (computed accent colour equals `#6D4AE0`, input border contrast at least 3:1, controls at least 44 px) (FR-043)

### Implementation for User Story 6

- [ ] T091 [US6] `FE/core/layout/shell.component.ts` (+ `.html`): pale lavender sidebar with menu (Master Data, Audit Log; unbuilt modules hidden), header with school name, current-year tag, user menu, `p-toast` outlet, router outlet with view transitions; a `currentYearGuard` that, when `/api/auth/me` returns no current year, redirects every route except Academic years, Change password and Logout to Academic years, disables the other menu items and shows the "create an academic year covering today" message, and re-checks after a year is saved (FR-013, FR-028, FR-037)
- [ ] T092 [P] [US6] `FE/core/layout/password-popup.component.ts`: after sign-in, while `passwordChanged` is false, opens a modal with "Change now" (opens the change-password dialog) and "Later" (closes it; it opens again at the next sign-in); not shown once the password is changed (FR-009)
- [ ] T093 [US6] `FE/core/layout/no-current-year.notice.ts` and link to the academic years page (US6 scenario 2)
- [ ] T094 [P] [US6] `FE/shared/ui/page-skeleton.component.ts` and use it in all list and form pages (FR-038)
- [ ] T095 [US6] Wire success and error toasts into every create, edit, delete and password action (FR-039), and field-level validation display via `FE/shared/forms/api-error.ts` (FR-040)
- [ ] T096 [P] [US6] Responsive pass of the shell and forms at 768 px and up in `FE/core/layout/shell.component.css` (FR-041)
- [ ] T097 [US6] Theme conformance pass: verify every page uses tokens from `FE/theme/campusflow-preset.ts` only (no hard-coded colours elsewhere) (FR-043)

**Checkpoint**: All six stories complete.

---

## Phase 9: Polish and Cross-Cutting Concerns

- [ ] T098 [P] Add the production build step: script `scripts/build-frontend.ps1` and `.sh` that runs `ng build` and copies the output to `management/src/main/resources/static`; add a test `BT/shared/SpaFallbackIT.java` (non-API path returns `index.html`, `/api/unknown` returns 404 JSON)
- [ ] T099 [P] Run `scripts/check-fr-coverage.ps1 -Strict`; add tests for any FR it reports uncovered, and switch `scripts/verify.*` to strict mode; FR-036 is cited by a small test of the check script itself (FR-036, SC-009)
- [ ] T100 Run `scripts/verify.ps1` with `JAVA_HOME` set to JDK 21 and fix failures
- [ ] T101 [P] Run the manual scenarios in `specs/001-foundation/quickstart.md` (1 to 12) and record results, including three timed checks: a first-time admin signs in and adds a year, standard and division in under 10 minutes unaided (SC-001, SC-008), and a filtered audit search is found within 30 seconds (SC-005); update the quickstart where steps differ
- [ ] T102 [P] Security review of `BE/security/` and `management/src/main/resources/application.properties` (cookie flags, CSRF, no secrets in logs, generic error messages) using the `security-review` skill
- [ ] T103 [P] Update `CLAUDE.md` Commands section with frontend commands (`npm start`, `npm run e2e`, build-frontend script) and the first-run credentials file location
- [ ] T104 (needs the owner's explicit go-ahead; do not run unattended) Open a pull request from `001-foundation` to `main` after `scripts/verify.ps1` passes

---

## Dependencies and Execution Order

- **Phase 1** has no dependencies. T013 is not needed (no mockup images); screens follow `docs/design/ui-tokens.md`.
- **Phase 2** depends on Phase 1 and blocks every story. T105 (test infrastructure) must finish before any integration test in a story runs. Manual owner actions in T001 (Node upgrade, test database) come first.
- **US1** starts after Phase 2. **US2** and **US3** need US1's `SecurityConfig` (T039) for authenticated tests, otherwise they are independent of each other. **US4** needs Phase 2 only (its test data comes from US1 and US2 events). **US5** needs Phase 2 plus at least one list (US2 or US4). **US6** needs US1 and the pages to link.
- Within a story: tests first (must fail), then entities/DTOs, services, controllers, then frontend.
- T014 (migration) precedes all entity tasks; T019 (AuditService) precedes any service that writes data; T021 and T023 precede every controller that exposes a list.

### Parallel opportunities

- Phase 1: T003 to T006, T009, T010, T012 together.
- Phase 2: T015 (needs T007 and T014) to T017, T020, T022, T024 to T026, T028, T029 once T014 is done.
- US1: T030 to T037 (all tests), then T045 to T048 (frontend) alongside backend T038 to T044.
- US2: T050 to T056 (tests), T057 to T059 (entities), T063 and T064 (pages).
- US4 tests T071 to T076 and US5 tests T081 to T085 can be written while US2 is being implemented.

## Implementation Strategy

- **MVP**: Phases 1 and 2, then US1 only. Stop and validate (sign in, sign out, lock-out, idle, password popup, developer reset).
- **Next increment**: US2 (the school's structure), then US4 (audit visibility) and US3.
- **Then**: US5 hardening of the table (performance), US6 polish, and Phase 9.
- Commit after every task with the FR id in the message; open the PR only after T100 passes.

## Notes

- FR coverage map: FR-001 to FR-009, FR-044, FR-045 in US1; FR-010 to FR-017, FR-020, FR-046 in US2; FR-018, FR-019 in US3; FR-021 to FR-027 in US4 (with FR-021, FR-023 also in US2); FR-029 to FR-035 in US5; FR-028, FR-037 to FR-043 in US6; FR-036 in T010 and T099.
- Seed value for `affects_fees` is `false` for all seven categories until the school confirms (T012, T014).
- Never use real student or staff data in fixtures (constitution Principle VI).
