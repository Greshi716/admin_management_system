Result: FAIL

# Test results: 001-foundation

Run 2026-10-25, branch `001-foundation`, JDK 21.0.11 (JAVA_HOME set), PostgreSQL 15.6 on localhost:5432, Windows 11. Port 8080 was free.

## Commands and outcome

| Step | Result |
|---|---|
| `bash scripts/verify.sh` | Exit 1. Stopped at step 1 (backend `mvnw verify`, BUILD FAILURE). Frontend and e2e steps in the script never ran. |
| `bash scripts/check-fr-coverage.sh --strict` | PASS: 46 of 46 FRs covered. |
| Extra (run by hand because verify stopped early): `npm ci && npm run build && npm test -- --watch=false` | PASS: build ok, 11 files, 78 tests passed, 0 failed. |
| Extra: `npm run e2e` (Playwright, own backend) | PASS: 68 passed, 0 failed, 0 skipped. |

## Counts

| Suite | Run | Passed | Failed | Skipped |
|---|---|---|---|---|
| Backend (surefire + failsafe as reported by Maven) | 201 | 199 | 2 | 0 |
| Frontend unit (Vitest) | 78 | 78 | 0 | 0 |
| Playwright e2e | 68 | 68 | 0 | 0 |

## New tests from test-implementation.md (never run before)

All five compiled and ran.

| Class | Tests | Result |
|---|---|---|
| `masterdata.AcademicYearBoundaryIT` | 5 | pass |
| `masterdata.StudentCategoryCodeIT` | 2 | FAIL (2 of 2), see T1 |
| `security.LockoutExpiryBoundaryIT` | 2 | pass |
| `security.NoUserManagementIT` | 1 | pass |
| `security.PasswordLengthBoundaryIT` | 2 | pass |

Total new: 12 run, 10 passed, 2 failed. The other 189 backend tests (the earlier suite) all passed.

## Findings

**T1. TEST BUG: `StudentCategoryCodeIT.aCodeWithASlashAndASpaceIsReturnedUnchanged` (line 41)**
Assertion: `GET /api/student-categories/{id}` expected 200, got 405. `StudentCategoryController` has no `GET /{id}` (only list, create, search, export, PUT, active, DELETE), and `specs/001-foundation/contracts/openapi.yaml` (`/student-categories/{id}`) defines only `put` and `delete`. The test calls an endpoint that is not in the contract. The create step (code `X/Y Z` returned unchanged on 201) passed.
Fix: in `management/src/test/java/com/admin/management/masterdata/StudentCategoryCodeIT.java`, read the row back with `jdbc` or `POST /api/student-categories/search` instead of GET by id.

**T2. TEST BUG: `StudentCategoryCodeIT.flippingTheFlagStoresItAndRaisesTheVersionByOne` (line 56)**
Assertion: `No value at JSON path "$.affectsFees"` on the same non-existent `GET /{id}` (405, empty body). The earlier steps passed: create returned version 0 and the PUT with `affectsFees:true` returned version 1. The final DB check on `affects_fees` was never reached. Same root cause as T1.
Fix: same file; replace the GET by id with a `search` call or rely on the `jdbc` assertion.

**T3. SPEC GAP (minor, no action needed to pass)**
FR-019 says the admin can "create, view and edit" categories, but the contract offers no single-record read for categories (the UI uses the list). If a single-record read is wanted, add it to the spec and contract first. The academic year controller has `GET /{id}`; categories and standards do not.

**T4. Process note: verify.sh did not reach the frontend steps**
Because the backend step failed, `verify.sh` never ran the frontend and e2e steps. I ran them separately (above); they passed. A full green `verify.sh` run is still needed after T1 and T2 are fixed.

No PRODUCT BUG and no ENVIRONMENT failures were found.

## Uncovered FRs

None. The strict coverage script reports 46 of 46.

## Skipped or disabled tests

None reported by Maven (0 skipped), Vitest or Playwright.

## Manual scenarios (specs/001-foundation/quickstart.md section 3)

`specs/001-foundation/quickstart-results.md` records scenarios 1 to 12 as passed on automated evidence only, so nobody walked them by hand. This run did not repeat any manual walk.
Still needing a human:
- Scenario 4 (10 minute idle) was not waited out in real time (shortened timeout in test).
- Scenario 12 (admin password reset procedure) was not run by hand.
- Scenario 13 (SC-008 timed check: add a year, divisions and a category in under 10 minutes, unaided).
- The SC-001 timed check (first sign-in plus year, standard and division in under 10 minutes) and the performance checks SC-006 and SC-007.
- Scenarios 1 to 3, 5 to 11 have automated evidence only; a human smoke pass is optional.

## Logs

Raw output: `C:\Users\Greshi\AppData\Local\Temp\verify.log`, `fe.log`, `e2e.log`.
