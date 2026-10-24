# Quickstart: Validating Foundation (F0)

A run-and-check guide for proving F0 works end to end. Contracts are in
[contracts/openapi.yaml](contracts/openapi.yaml); tables are in [data-model.md](data-model.md).

## Prerequisites

- JDK 21 (`JAVA_HOME` must point to JDK 21; the shell default may be 17)
- Node 22.22.3 or newer (or 24 LTS); Angular 22 will not install on 22.22.2
- PostgreSQL 15+ running on `localhost:5432` with two empty databases:
  `admin_management_db` (dev) and `admin_management_test` (tests)
- UI design reference: `docs/design/ui-tokens.md` (no mockup images are needed)

## 1. Backend checks

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
cd management
.\mvnw.cmd verify          # runs Flyway migrations and integration tests on admin_management_test
```

Expected: build success; tests cover the FRs listed in step 6.

## 2. Run the app (dev)

```powershell
cd management ; .\mvnw.cmd spring-boot:run     # API on http://localhost:8080
cd frontend   ; npm install ; npm start        # UI on http://localhost:4200, /api proxied to 8080
```

First start with an empty `users` table writes `credentials-first-run.txt` in the application
data directory (path printed in the log) with the generated Admin password.
Read it once, then delete it.

## 3. Manual smoke scenarios

| # | Do this | Expect | Covers |
|---|---|---|---|
| 1 | Open the app signed out, then any screen address | Sign-in page | FR-001, FR-007, SC-002 |
| 2 | Sign in with the generated password | App shell and a popup asking to change the password | FR-003, FR-009, SC-001 |
| 3 | Five wrong passwords, then the correct one | Pause message with retry time | FR-044, SC-010 |
| 4 | Leave the app idle for 10 minutes, then click | Back at sign-in; audit shows idle sign-out | FR-008, SC-011 |
| 5 | Add academic years 2026-27 and an overlapping one | Second refused, message names the first | FR-010, FR-011 |
| 6 | Add 2027-28 (01-06-2027 to 31-05-2028) next to 2026-27, check which year is current today, then edit the dates so today is inside no year | The year containing today is current; with none, the app forces the admin to the Academic years create form and blocks other screens until a year covering today is saved | FR-012, FR-013, SC-003 |
| 7 | Add standards, divisions; delete a used year | Delete refused with "in use"; a standard, division or category can be marked inactive instead | FR-014..FR-017, FR-020 |
| 8 | Open Student Categories | Seven seeded categories | FR-018 |
| 9 | Edit the same record in two windows | Second save refused with latest values shown | FR-046 |
| 10 | Open Audit Log, filter by today, area Security | Login and failed-login entries; no edit/delete control | FR-021..FR-026 |
| 11 | In any list: search, filter a column, sort, export | Excel has all matching rows and all columns | FR-029..FR-033 |
| 12 | Start once with `campusflow.reset-admin-password=true` (developer procedure, `docs/runbooks/reset-admin-password.md`) | New password in the credentials file, old password rejected, popup shown again, reset audited | FR-045 |

## 4. Automated UI tests

```powershell
cd frontend
npx playwright install chromium     # once
npm run e2e                         # starts backend (test profile) and UI, runs specs in e2e/
```

## 5. Whole-repo check

```powershell
.\scripts\verify.ps1                # backend verify + frontend build/tests + FR coverage check
```

## 6. FR coverage gate

`scripts/verify.*` scans `spec.md` for `FR-xxx` and the tests (`@Covers("FR-xxx")`, Playwright
titles) and fails if any FR has no reference (FR-036, SC-009).

## Performance checks (SC-006, SC-007)

Load 10,000 synthetic audit rows with the test data helper, then confirm a page, search,
filter or sort returns in under 2 s and a full export finishes in under 30 s. Never use real
school data.
