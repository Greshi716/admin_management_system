# Quickstart results: Foundation (F0)

Run on 2026-10-25, branch `001-foundation`, JDK 21.0.11, PostgreSQL 15 on localhost, Windows 11.
Method: **automated evidence only**. Nobody walked the app by hand for this record, so the three timed checks
at the end are still open. The scenario numbers are those in [quickstart.md](quickstart.md) section 3.

Whole-repo result: `scripts/verify.ps1` passed (backend 179 tests, frontend 76 unit tests, FR coverage 46 of 46
in strict mode). The Playwright suite (`npm run e2e`) passed in full on the previous run (66 tests); it runs the
real backend (test profile) and the real UI.

| # | Scenario | Automated evidence | Result |
|---|---|---|---|
| 1 | Signed out opens the sign-in page | `auth.spec.ts`, `LoginIT` | Pass |
| 2 | Sign in, shell and password popup | `auth.spec.ts`, `shell.spec.ts` (FR-009), `FirstRunBootstrapIT` | Pass |
| 3 | Five wrong passwords, pause message | `LockoutIT`, `auth.spec.ts` | Pass |
| 4 | Idle 10 minutes, back at sign-in, audited | `IdleTimeoutIT` (real server, shortened timeout), `idle-watch.service.spec.ts` | Pass (not waited out in real time) |
| 5 | Overlapping academic year refused | `AcademicYearIT`, `academic-years.spec.ts` | Pass |
| 6 | Current year, none covering today blocks the app | `AcademicYearIT`, `shell.spec.ts` (FR-013) | Pass |
| 7 | Used year cannot be deleted; inactive instead | `DivisionIT`, `standards.spec.ts`, `divisions.spec.ts` | Pass |
| 8 | Seven seeded student categories | `StudentCategoryIT`, `student-categories.spec.ts` | Pass |
| 9 | Same record in two windows, second save refused | `MasterDataConflictIT`, `*.spec.ts` for each list | Pass |
| 10 | Audit log filter by date and area, no edit controls | `AuditLogQueryIT`, `AuditAppendOnlyIT`, `audit-log.spec.ts` | Pass |
| 11 | Search, filter, sort, export in a list | `TableQueryIT`, `TableExportIT`, `table.spec.ts` (export compared with screen) | Pass |
| 12 | Admin password reset procedure | `AdminResetIT`; runbook `docs/runbooks/reset-admin-password.md` | Pass (procedure itself not run by hand) |

## Timed checks still open (need a person, unaided)

| Check | Target | Status |
|---|---|---|
| First-time admin signs in and adds a year, standard and division | under 10 minutes (SC-001, SC-008) | Not done |
| Filtered audit search finds a given entry | within 30 seconds (SC-005) | Not done |

## Performance (SC-006, SC-007)

Measured by `TablePerformanceIT` on 10,000 synthetic rows (see `management/target/performance-report.txt` after
`./mvnw verify -Dtest.groups=performance -Dtest.excludedGroups=none`): page 18 ms, search 133 ms, filter 14 ms,
sort 17 ms, export 645 ms.

## Differences from quickstart.md

- Section 2 says `npm install`; the repository scripts use `npm ci`.
- Scenario 3 and 4 wording is unchanged; the tests shorten the waiting time instead of waiting it out.
- Production build: `scripts/build-frontend.ps1` copies the Angular build into the backend static folder.
