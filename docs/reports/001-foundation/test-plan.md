Result: PLAN READY

# Test plan: 001-foundation (F0)

Source: `specs/001-foundation/spec.md` and `docs/decision-log.md` only. Not planned from code.
All data is synthetic (users such as `admin`, years such as `2026-27`, students/salaries are out of scope). Passwords in tests are generated fixtures, never real.

Levels: UNIT, INT (integration, DB), API (contract), E2E (browser), MAN (manual), PERF.
Time zone for all date tests: Asia/Kolkata. Display date format: dd-MM-yyyy. A controllable clock is needed for date and idle/lock-out tests.

## 1. Functional requirement cases

Each FR has N (normal), B (boundary), F (failure). Format: Given / When / Then in one line; expected result has concrete values.

### Sign-in (FR-001 to FR-009, FR-044, FR-045)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-001-N | Sign in with valid user | API, E2E | user `admin` exists with known test password / POST login with right credentials / session created | HTTP 200, session cookie set, GET /api/academic-years then returns 200 |
| FR-001-B | Username case and whitespace | API | user `admin` / login with `Admin` and with ` admin ` / compare | NEEDS-CLARIFICATION Q1; whichever rule is chosen is asserted. Empty username or empty password returns 400 with field errors, never 500 |
| FR-001-F | Any data endpoint without sign-in | API | no session / GET each endpoint in openapi.yaml except login / response | every one returns 401, body contains no school data |
| FR-002-N | Password stored as hash | INT | admin created with password `Test#12345` / read `users.password_hash` / inspect | value is not `Test#12345`, starts with a recognised hash prefix (e.g. `$2`), length > 50 |
| FR-002-B | Same password twice gives different hashes | UNIT | encode `Test#12345` twice / compare | two different strings, both match the password |
| FR-002-F | Password never in logs or responses | INT | login success and failure, password change / capture app log and all responses / search | neither password nor hash text appears in any log line, response or audit row |
| FR-003-N | Default Admin created on fresh DB | INT | empty DB / app starts / query users | exactly 1 user, username as documented, role `ADMIN`, active=true, password_changed=false |
| FR-003-B | Second start does not create a second user or new password | INT | DB already has admin / restart / query | still 1 user, hash unchanged, no new credentials file |
| FR-003-F | No endpoint to create users or roles | API | signed in / POST /api/users, /api/roles / response | 404 or 405; no role-based 403 logic needed |
| FR-004-N | Active user can sign in | API | active user / valid login / result | 200 |
| FR-004-B | Wrong user vs wrong password give identical response | API | `nouser` and `admin`+wrong password / login / compare | same status 401, same message text, same body shape, similar timing (no gross difference) |
| FR-004-F | Inactive user refused | API | `users.active=false` / valid credentials / login | 401 with the same generic message; no session; failed attempt audited |
| FR-005-N | Last login updated | INT | last_login null / successful login / read user | last_login within 5 seconds of now (IST) |
| FR-005-B | Failed login does not update last login | INT | last_login = T1 / failed login / read | last_login still T1 |
| FR-005-F | Refused inactive user does not update last login | INT | inactive user / valid password / read | last_login unchanged |
| FR-006-N | Logout ends session | API, E2E | signed in / POST logout then GET data / result | logout 200/204; next GET returns 401 |
| FR-006-B | Browser Back after logout | E2E | signed in, logout / press Back / page | sign-in page or redirect; no cached school data visible; protected response has no-store cache header |
| FR-006-F | Reuse old session cookie after logout | API | captured cookie, logout / replay cookie / result | 401 |
| FR-007-N | Unauthenticated deep link | E2E | signed out / open /master-data/standards / result | redirected to /login |
| FR-007-B | Every route in the router | E2E | signed out / open each route (incl. audit log, academic years, unknown path) / result | all land on /login; none flashes data |
| FR-007-F | After sign-in, deep link return | E2E | signed out, opened /audit-log / sign in / result | NEEDS-CLARIFICATION Q2 (return to target or home) |
| FR-008-N | Idle 10 minutes signs out | INT, E2E | signed in at 10:00 / next action at 10:10:01 with fake clock / result | 401, UI goes to /login, Security Login-action audit entry "idle sign-out" exists |
| FR-008-B | Exactly 9:59 vs 10:01 | INT | last activity 10:00:00 / action at 10:09:59 then another at 10:20:00 / result | first succeeds (and resets timer); second at 10:20:00 after 10:09:59 + 10:01 gap is refused |
| FR-008-F | Idle sign-out when audit cannot be saved | INT | idle expired, audit write forced to fail / action / result | NEEDS-CLARIFICATION Q3; at minimum no data is returned |
| FR-009-N | Random default password written once | INT | fresh DB / startup / read credentials-first-run.txt | file exists in `campusflow.data-dir`, password >= 12 chars, differs across two fresh installs, works for login |
| FR-009-B | New password length 8 accepted; 7 refused | API | signed in / change to 8-char then 7-char value / result | 8 chars: 200; 7 chars: 400 field error "at least 8 characters". NEEDS-CLARIFICATION Q4 on max length and complexity |
| FR-009-F | New equals current; wrong current password | API | signed in / change to same password; change with wrong current password / result | both refused 400/422 with clear messages; password unchanged |
| FR-009-N2 | Popup after sign-in until changed | E2E | password_changed=false / sign in; click Later; sign out and in; Change now and save; sign in again / observe | popup shown on first two sign-ins, not after the change; no navigation is blocked by it |
| FR-009-N3 | Credentials file removed on change | INT | credentials file exists / change password / check file | file deleted (or overwritten, no password inside) |
| FR-009-F2 | Credentials file missing or read-only at change | INT | file absent / change password / result | change still succeeds, no error 500 |
| FR-044-N | 5 failures lock for 5 minutes | API | 5 wrong attempts / 6th attempt with the CORRECT password / result | 6th refused (423/429 or documented code), message contains time to retry (dd-MM-yyyy HH:mm or "in N minutes"), lock audit entry exists |
| FR-044-B | 4 failures do not lock; success resets counter | API | 4 failures then a success then 4 failures / 9th attempt valid / result | all succeed or fail as normal, no lock; counter reset by success |
| FR-044-B2 | Lock expiry boundary | INT | locked at 10:00:00 / attempt at 10:04:59 and 10:05:00 (fake clock) / result | 10:04:59 refused; 10:05:00 allowed (NEEDS-CLARIFICATION Q5 whether exactly 5:00 is allowed) |
| FR-044-F | Attempts during the pause | API | locked / 3 more wrong attempts / check lock | lock end time not extended (NEEDS-CLARIFICATION Q6); no 500 |
| FR-045-N | Developer reset | INT | admin with changed password / start with the reset setting / read file, user, audit | new random password in file, old password no longer works, password_changed=false, Security audit entry exists, no password text in it |
| FR-045-B | Reset clears an active lock | INT | account locked / reset / login with new password | NEEDS-CLARIFICATION Q7; result asserted once decided |
| FR-045-F | Reset setting off by default; test profile guard | INT | normal profile / start without setting / check; test profile with DB name not ending `_test` / start | password unchanged; app refuses to start (decision log 2026-10-25) |

### Academic years (FR-010 to FR-013)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-010-N | Create year | API, E2E | none / POST name `2026-27`, 01-06-2026, 31-05-2027 / GET list | 201; list shows it, dates displayed 01-06-2026 and 31-05-2027 |
| FR-010-B | Name length and required fields | API | none / empty name, missing start, missing end; name of max length +1 / result | each: 400 with the field named; max length accepted (NEEDS-CLARIFICATION Q8 name max and format) |
| FR-010-F | Duplicate name | API | `2026-27` exists / create again with non-overlapping dates / result | 409 or 400 duplicate message; name comparison case-insensitive per Q9 |
| FR-010-V | View and edit | API | year exists / PUT with new name and version / GET | 200, new name stored, version incremented by 1 |
| FR-011-N | Valid dates accepted | API | none / start 01-06-2026, end 31-05-2027 / save | 201 |
| FR-011-B | End equals start; end one day after | API | none / start=end=01-06-2026; then end=02-06-2026 / save | equal: 400 "end must be after start"; one day later: 201 |
| FR-011-B2 | Adjacent years | API | 2025-26 ends 31-05-2026 / create 2026-27 starting 01-06-2026 / save | 201 (no overlap). Starting 31-05-2026: 409 overlap naming `2025-26` |
| FR-011-F | Overlap inside, crossing both ends, enclosing | API | 2026-27 exists / save years 01-09-2026..31-12-2026, 01-01-2026..15-06-2026, 01-01-2026..31-12-2028 / result | all refused with message containing `2026-27`; none stored |
| FR-011-F2 | Edit does not overlap itself | API | 2026-27 exists / edit its end to 30-06-2027 / save | 200 (a year does not overlap itself) |
| FR-012-N | Current year from today | INT | years 2025-26, 2026-27, clock 15-08-2026 IST / GET current / result | `2026-27` |
| FR-012-B | First day, last day, midnight rollover | INT | same years / clock 31-05-2026 23:59:59 IST, 01-06-2026 00:00:00 IST, 01-06-2025, 31-05-2027 / result | `2025-26`, `2026-27`, `2025-26`, `2026-27`; start and end dates are inclusive |
| FR-012-B2 | UTC vs IST edge | INT | server JVM timezone UTC / clock 31-05-2026 19:00 UTC (= 01-06-2026 00:30 IST) / result | `2026-27` (school date is IST) |
| FR-012-F | Not stored, not settable | API, INT | schema and API / look for is_current column and PUT current endpoint / result | no column, no endpoint; payload with `current:true` ignored or 400 |
| FR-013-N | No year covers today | API, E2E | years only in the past / sign in / result | current-year endpoint returns "none"; UI opens Academic years with create form; menu items disabled except Change password and Logout |
| FR-013-B | Gap between two years | INT | 2025-26 ends 31-05-2026, 2026-27 starts 15-06-2026, clock 05-06-2026 / current / result | none; app does not choose either year |
| FR-013-B2 | Block lifts on save | E2E | blocked / create year containing today / result | other menu items enabled at once without re-login; header shows the new year |
| FR-013-F | Direct link while blocked | E2E, API | blocked / open /master-data/standards; call GET /api/standards / result | UI redirects to Academic years; NEEDS-CLARIFICATION Q10 whether API returns 403 or data for non-year endpoints (spec says "web app" enforces) |

### Standards (FR-014, FR-015)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-014-N | Create standard with stream | API | none / name `11`, order 11, stream Science / save, list | 201; list shows stream Science |
| FR-014-B | Stream optional; stream only Science or Commerce | API | none / standard `5` no stream; stream `Arts` / save | `5` saved with null stream; `Arts` rejected 400. NEEDS-CLARIFICATION Q11 on whether stream is allowed for 1-10 |
| FR-014-F | Missing name or display order; negative or non-numeric order | API | none / each case / save | 400 per field; order `0` per Q12 |
| FR-015-N | Default listing order | API | standards created with orders 3,1,2 / GET list without sort / order | returned 1,2,3 |
| FR-015-B | Equal display order tie-break | API | two standards both order 5 / GET / order | stable and deterministic (by name or id; NEEDS-CLARIFICATION Q13) |
| FR-015-F | Duplicate name | API | `11` exists / create `11` Commerce | 409 duplicate (NEEDS-CLARIFICATION Q14: is "11 Science" and "11 Commerce" allowed as two rows with same name? Spec says names unique) |

### Divisions (FR-016, FR-017)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-016-N | Create division | API | year 2026-27 and standard 5 exist / create `A` / list | 201, shows year, standard and name |
| FR-016-B | Name length, case | API | none / name `A`, `a`, 1 char and max+1 / save | NEEDS-CLARIFICATION Q15 (max length, case rule) |
| FR-016-F | Non-existent year or standard id | API | none / create with year id 99999 / result | 400 or 404 with field message; nothing stored |
| FR-016-N2 | Form shows only existing options | E2E | 2 years, 3 standards (one inactive) / open add form / dropdowns | exactly the existing years; inactive standard hidden from choices (FR-020) |
| FR-017-N | Same name in other year / other standard | API | `A` for std 5 in 2026-27 / create `A` for std 5 in 2025-26; `A` for std 6 in 2026-27 / result | both 201 |
| FR-017-B | Case/space variants | API | `A` exists / create `a` and ` A ` / result | refused if Q15 says case-insensitive and trimmed |
| FR-017-F | Duplicate | API | `A`, std 5, 2026-27 exists / create again / result | 409 duplicate; also when edit renames B to A |

### Student categories (FR-018, FR-019)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-018-N | Seven seeded | INT, E2E | fresh DB / GET categories / compare | exactly O/S, CEW, W, STAFF, S STAFF, RTE, CASUAL; all affects_fees=false (decision log 2026-10-24) |
| FR-018-B | Count stays 7 after restart | INT | seeded / restart twice / count | 7, no duplicates; edits made by admin are not reverted |
| FR-018-F | Seed does not overwrite admin edits | INT | admin renamed W / restart / read | the rename remains |
| FR-019-N | Create with flag | API | none / code `NEW1`, name `New one`, flag true / save | 201, flag true shown |
| FR-019-B | Code with space and slash (`S STAFF`, `O/S`) round-trips; max length | API | none / create code `X/Y Z` and GET by id | stored and returned unchanged; max+1 refused (Q16) |
| FR-019-F | Duplicate code; missing field; flag missing | API | `RTE` exists / create `RTE`; omit name; omit flag / result | 409; 400 name required; 400 flag required (null is not false) |
| FR-019-V | Edit | API | category exists / flip flag with version / GET | updated, version +1 |

### Removal (FR-020, FR-046)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-020-N | Delete unreferenced record | API | standard with no divisions / DELETE / GET | 204 then 404; audit Delete entry with old values |
| FR-020-B | Last reference removed makes delete possible | INT | standard referenced by one division / delete the division then the standard / result | both succeed |
| FR-020-F | Delete referenced year, standard, division/category | INT, API | year with divisions; standard with divisions / DELETE / result | 409 "in use", record intact, no audit Delete. Division and category referenced by "later records" cannot exist yet; use a test-only referencing table or mark NEEDS-CLARIFICATION Q17 |
| FR-020-N2 | Inactive standard/division/category | API | records exist / PATCH inactive / list for choices | hidden from choices, still returned in history list; Edit/inactive audited |
| FR-020-F2 | Academic year inactive | API | year exists / try to set inactive / result | no such field or endpoint; 400/404/405 |
| FR-020-F3 | No hard delete of users/audit via API | API | signed in / DELETE any audit id | 404/405 |
| FR-046-N | Stale edit refused | API, E2E | two windows load standard v1 / window A saves, window B saves / result | B gets 409, message "record has changed", response contains latest values; DB holds A's values |
| FR-046-B | Version without change; save after refresh | API | B reloads latest v2 / saves / result | 200, version 3 |
| FR-046-F | Every editable record type | API | year, standard, division, category / stale save for each / result | all four return 409 with latest values; missing version field returns 400 |
| FR-046-F2 | Delete or inactive on a stale record | API | stale version / DELETE or PATCH inactive / result | NEEDS-CLARIFICATION Q18 (spec says "edit" only) |

### Audit (FR-021 to FR-027)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-021-N | Create, edit, delete, inactive each audited | INT | signed in / create, edit, inactive, delete a standard / read audit | 4 rows: actions Create, Edit, Edit(or inactive) and Delete, area `Master data`, record type, record id, user, IST timestamp; Create has null old / non-null new; Delete has non-null old / null new; Edit has both |
| FR-021-B | Edit with no real change | INT | standard unchanged / PUT same values / audit | NEEDS-CLARIFICATION Q19 (entry or no entry) |
| FR-021-F | Old/new values hold only changed or all fields | INT | edit name only / read old, new / compare | old has name `5`, new has `5th`; NEEDS-CLARIFICATION Q20 on full snapshot vs changed fields |
| FR-022-N | Every sign-in/out/pause/export | INT | scenarios of sign-in success, failure, logout, idle, lock, export / read audit | area Security, action Login for the first five; action Export for export; one row each |
| FR-022-B | Failed login for unknown username | INT | `nouser` / fail / audit | row exists; user shown as null or attempted name (Q21); the attempted password is not stored |
| FR-022-F | Password change entry | INT | change password / audit | action Edit, area Security, record type user; old/new say "changed" with no hash/pwd substring |
| FR-023-N | Same transaction, success | INT | create standard / commit / audit | both exist |
| FR-023-B | Change fails (constraint) | INT | duplicate name create / fail / audit count | no audit row for it |
| FR-023-F | Audit write fails | INT | force audit insert to throw (test hook or trigger) / edit standard / result | change rolled back; standard unchanged; HTTP 500 handled with generic message |
| FR-023-F2 | Atomicity of password reset and password change | INT | audit insert forced to fail / reset or change password / check | password unchanged |
| FR-024-N | No edit/delete API | API | signed in / PUT/DELETE/PATCH /api/audit-log/{id} / result | 404/405 |
| FR-024-B | DB-level protection | INT | direct SQL UPDATE/DELETE/TRUNCATE on audit_log as app DB user / try | refused (trigger `V5`, per decision history) |
| FR-024-F | UI shows no edit/delete control | E2E | audit screen / look at rows and header / controls | none |
| FR-025-N | Masking last 4 | UNIT | masker with `1234567890123456` / mask / output | `************3456` (or documented mask); only last 4 visible |
| FR-025-B | Value length 0, 3, 4, 5 | UNIT | masker / each length / output | empty -> empty; 3 and 4 chars fully hidden or per Q22 (spec: last 4 visible; 4 chars would reveal all, NEEDS-CLARIFICATION Q22); 5 -> `*` + last 4 |
| FR-025-F | Password/hash never, in any form | INT | password change / grep audit old/new JSON, also length or prefix | no hash, no masked tail, no length |
| FR-026-N | Filters by range, month, year, area, action | API, E2E | entries on 31-05-2026, 01-06-2026, 15-06-2026, 10-01-2027 / each filter / result | range 01-06..30-06-2026: 2 rows; month 06-2026: 2 rows; year 2026: 3 rows; area Security only; action Export only |
| FR-026-B | Boundary dates inclusive; entry at 23:59:59 IST and 00:00:00 IST | API | entries at those times / filter from=to=same day / result | each day gets its own entry only (IST day, not UTC) |
| FR-026-F | from after to | API, E2E | none / from=02-06-2026, to=01-06-2026 / result | 400 with clear message; UI shows message and no rows, no crash |
| FR-027-N | Later area and actions | INT | write entries area `Fees` action Cancel, Finalise, Import with reason / read | accepted without schema change; reason stored |
| FR-027-B | Reason optional; long reason | INT | reason null; reason at limit / write | stored (limit Q23) |
| FR-027-F | Unknown area or action | INT | area `Foo` / write | refused or NEEDS-CLARIFICATION Q24 |

### App shell (FR-028, FR-037 to FR-043)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-028-N | Shell elements on every screen | E2E | signed in with year 2026-27 / visit each screen / check | menu, user full name, `2026-27`, Change password, Logout visible on all |
| FR-028-B | No year | E2E | no current year / any allowed screen / check | blocking message shown instead of year; only Change password and Logout enabled |
| FR-028-F | Year rolls over while screen is open | E2E | clock moves from 31-05 to 01-06 / next navigation / check | header shows new year; NEEDS-CLARIFICATION Q25 if it should refresh without navigation |
| FR-037-N | No full page reload | E2E | on one screen / navigate to another / check | no browser load event; in-memory flag survives |
| FR-037-F | Reload on a deep link keeps the session | E2E | signed in / F5 on /audit-log / result | still signed in, same screen |
| FR-038-N | Loading state | E2E | throttled/delayed API 2 s / open list and form / look | spinner or skeleton visible, never blank |
| FR-038-F | API error | E2E | API returns 500 / open list / look | error message with retry, not blank |
| FR-039-N | Save feedback | E2E | valid save; save that fails (duplicate) / look | success message; error message text from server |
| FR-040-N | Inline validation | E2E | form / type 7-char password; empty name / look | message beside the field before submit |
| FR-041-N | Desktop and tablet | E2E, MAN | viewports 1366x768 and 768x1024 / open every screen / look | no horizontal page scroll, controls at least 44px tall, menu usable |
| FR-042-N | Gujarati display | E2E, MAN | division name `અ` and category name `વિદ્યાર્થી` / save, list, audit log, export / look | text identical in UI, audit values, and Excel; no tofu boxes |
| FR-043-N | Consistent style | MAN | review all screens against `docs/design/ui-tokens.md` / compare accent `#6D4AE0`, font | consistent |
| FR-003-UI | Navigation of unbuilt modules | E2E | signed in / view menu | not-yet-built modules absent or marked unavailable (US6 scenario 3) |

### Standard table (FR-029 to FR-035)

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-029-N | Paging | API, E2E | 60 rows / default request / result | 25 rows, total 60, page 2 gives 25, page 3 gives 10 |
| FR-029-B | Page sizes and exact multiples | API | 100 rows / sizes 10, 25, 50, 100 / result; 25 rows / size 25; 0 rows | pages = 10, 4, 2, 1; 25 rows -> 1 full page; 0 rows -> empty, total 0; page beyond last returns empty (not 500) |
| FR-029-F | Invalid sizes | API | none / size 0, -1, 30, 1000, `abc`; page -1 | 400 (or clamped; NEEDS-CLARIFICATION Q26) |
| FR-030-N | Search | API | rows `Science`, `Commerce` / q=`sci` / result | 1 row, total 1, case-insensitive |
| FR-030-B | Search empty/whitespace; across visible text columns only | API | rows / q=`` and q=`  `; q matches a non-text or hidden column / result | blank = no filter; numeric-only columns not searched (Q27) |
| FR-030-F | Special characters as plain text | API | rows with `50%`, `a_b`, `O/S`, `'; DROP TABLE` / each search / result | each matches literally (`%` not wildcard, `_` not single-char), no error, no injection |
| FR-030-G | Gujarati search | API | row `વિદ્યાર્થી` / q=`વિદ્યા` / result | matches |
| FR-031-N | Filter and search combine | API | rows / q=`s` + filter stream=Science / result | only rows satisfying both |
| FR-031-B | Filter on every column incl. dates, booleans | API | each list / one filter per column / result | each returns the correct subset (count asserted per column) |
| FR-031-F | Unknown column or bad value | API | none / filter col `nope`; boolean `maybe`; date `31-02-2026` | 400 with message; no 500, no raw SQL error |
| FR-032-N | Sort across pages | API | 60 rows / sort name asc, page 2 / result | rows 26-50 of the globally sorted list |
| FR-032-B | Asc, then desc; ties; null values | API | rows with equal and null values / sort / result | deterministic ordering; nulls placed consistently (Q28); Gujarati sorted in a defined collation |
| FR-032-F | Unsortable/unknown column | API | sort=`nope`, sort injection `name;drop` | 400 |
| FR-033-N | Export all matching rows | API | 60 rows, q and sort active / export / read xlsx | row count = matches (not 25), order = sort, header row present |
| FR-033-B | Hidden column included; paging ignored | API | column hidden on screen / export / read | hidden column present; sensitive columns masked; header names equal column labels |
| FR-033-B2 | Empty result | API | no matches / export / read | file with headings only, 0 data rows |
| FR-033-F | Invalid export params; unauthenticated | API | from>to; no session | 400; 401 |
| FR-033-G | Gujarati and formula-like text in cells | API | cell `વિદ્યાર્થી`, cell `=1+1`, `@x` / export / read | Gujarati intact; formula text stored as text, not evaluated |
| FR-033-D | Dates and numbers in the file | API | date and numeric columns / export / read | dates dd-MM-yyyy or true date cells with that format; no float rounding artefacts |
| FR-034-N | New list by declaring columns | UNIT, INT | test-only entity with 3 declared columns / call generic endpoint / result | paging, search, filter, sort and export all work with no per-list code |
| FR-034-F | Misconfigured column declaration | UNIT | duplicate key / unknown type / startup or call | clear error at startup/test |
| FR-035-N | Responsiveness with many rows | PERF | 10,000 rows / page, search, filter, sort / time | each < 2 s (see SC-006) |
| FR-035-B | Only one page loaded | INT | 10,000 rows / list request / measure | response contains at most page-size rows; query uses LIMIT |

### Cross-cutting requirement

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-036-N | Every FR has a test reference | UNIT (script) | all test sources / scan for `FR-0nn` | each of FR-001..FR-046 appears in at least one test name or tag; script fails otherwise |
| FR-036-B | Numbering gaps | UNIT | spec has FR-001..FR-046 / list | no FR missing from the matrix (FR-044, FR-045, FR-046 are placed after FR-043 in the spec) |
| FR-036-F | A test with no matching FR | UNIT | tags / scan | no reference to an FR that is not in the spec |

## 2. Success criteria

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| SC-001 | First sign-in within 1 minute | E2E, MAN | fresh install, read credentials file / open app, sign in / time | home screen in < 60 s from opening the sign-in page |
| SC-002 | 100% screens unreachable when signed out | E2E, API | list of all routes and all API paths from openapi.yaml / call unauthenticated | all routes redirect; all API paths (except login and health if documented) return 401; count of exceptions = 0 |
| SC-003 | Current year in 100% of dates | INT | years 2025-26, 2026-27, gap year / dates 31-05-2026, 01-06-2026, first and last day, a gap day, a day before all years / result | one year or none, never two |
| SC-004 | Audit completeness | INT | run one of each action type (create, edit, delete, inactive, login ok, login fail, logout, idle, pause, export, password change) and one failed change / count | count(audit) = count(successful actions) + security events; 0 entries for the failed change |
| SC-005 | Find an entry in 30 seconds | E2E, MAN | 500 entries over 90 days / use date filter to find one / time | < 30 s, 3 or fewer interactions |
| SC-006 | 10,000-row list < 2 s | PERF | 10,000 audit or test rows / page, search, filter, sort x20 / measure p95 | p95 < 2000 ms on the target spec PC (4 GB RAM, Windows 10); record machine spec in report (T083) |
| SC-007 | Export 10,000 rows < 30 s | PERF | 10,000 matching rows / export / time; compare | < 30 s; file has exactly 10,000 data rows in the sort order, all columns; memory stays within limit; other screens respond (<2 s) during export |
| SC-008 | Add year, divisions, category in 10 minutes | MAN | new user / add 1 year, 3 divisions, 1 category without help / time | < 10 minutes |
| SC-009 | Every FR has a passing test | UNIT (script) | FR-036 scan + test results / compare | 46 of 46 FRs covered and green |
| SC-010 | Lock after 5 failures, 100% | API | 20 repeated runs of the 5+1 sequence with varying case / result | sixth attempt refused each time |
| SC-011 | Idle sign-out 100% | INT | 20 runs with varying idle times 10:00..10:30 / next action / result | all refused as expired; 9:59 in each run is not expired |

## 3. Cross-cutting cases

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| X-MONEY-1 | No money in F0 | INT | schema review / scan columns | no float/double columns in any migration; any NUMERIC is NUMERIC(12,2). F0 has no money fields, so this is a schema guard test for later features |
| X-MONEY-2 | Rounding helper, if present | UNIT | values 0.5, 1.5, 2.5, -0.5, 100.495 / round(x,0) HALF_UP | 1, 2, 3, -1 (HALF_UP away from zero), 100 for scale 0; BigDecimal type only. Mark N/A if F0 has no helper |
| X-AUDIT-TX | Audit in same transaction | INT | see FR-023 | rollback of either leaves neither |
| X-LOCK-1 | Optimistic locking each entity | API | see FR-046 | 409 for year, standard, division, category; version field present in GET |
| X-LOCK-2 | Concurrent saves in parallel | INT | two threads same version / save simultaneously / result | exactly one 200, one 409; one audit row |
| X-DATE-1 | Time zone | INT | JVM zone UTC and Asia/Kolkata / timestamps in audit and "today" / compare | stored instants consistent; school date computed in Asia/Kolkata |
| X-DATE-2 | Date format | E2E | all screens with dates / read | dd-MM-yyyy (e.g. 01-06-2026); API uses ISO yyyy-MM-dd or as the OpenAPI says; typed input `31-02-2026` rejected, `29-02-2028` accepted, `29-02-2027` rejected |
| X-DATE-3 | Audit timestamp display | E2E | entry at 23:30 IST on 31-05-2026 / view | shows 31-05-2026 23:30, not next day |
| X-GU-1 | Gujarati round trip | API, E2E | name with Gujarati conjuncts (e.g. `ક્ષત્રિય`) / save, read, search, sort, export, audit | exact same code points everywhere; DB encoding UTF-8 |
| X-PERM-1 | Signed in vs not | API | every endpoint / call without session / result | 401 (see SC-002) |
| X-PERM-2 | CSRF / cross-origin | API | signed in cookie / state-changing request without the CSRF token or from another origin / result | refused (NEEDS-CLARIFICATION Q29 if CSRF protection is meant) |
| X-PERM-3 | Session cookie flags | API | login response / inspect | HttpOnly set; SameSite set; Secure per deployment (plain HTTP on LAN: Q30) |
| X-LOG-1 | No secrets in logs | INT | run login, change, reset, startup / read all log output | no password, hash, cookie value, credentials-file content |
| X-LOG-2 | Error responses | API | cause 500 and 400 / read bodies | no stack traces, SQL or file paths |
| X-LOG-3 | Credentials file not in git | MAN | git ls-files / check | `credentials-first-run.txt` and `data/` ignored |
| X-SQL | Injection | API | q, filter, sort params with `' OR 1=1 --` / call | treated as text or 400; no extra rows |
| X-DATA | Synthetic data only | MAN | review fixtures and seed / scan | no real names, Aadhaar, salary |

## 4. Edge cases from the spec

| ID | Edge case | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| EC-1 | Two windows edit same record | E2E | two sessions same record / both edit / second saves | 409 message, latest values shown (also FR-046) |
| EC-2 | Idle session | INT | see FR-008 | signed out after 10 minutes, audited |
| EC-3 | Removing master data in use | API | see FR-020 | delete refused; inactive allowed for standard, division, category; not for year |
| EC-4 | Default credentials | INT | see FR-009 | random, shown once, not forced to change |
| EC-5 | Many failed sign-ins | API | see FR-044/045 | pause after 5; developer reset |
| EC-6 | Very large export | PERF | 50,000 rows / export while loading another list in parallel | all 50,000 rows present; the other list responds in < 2 s; no OutOfMemoryError at 4 GB machine settings |
| EC-7 | Special characters and Gujarati in search/filter | API | see FR-030-F, FR-030-G | literal match |
| EC-8 | Date filter from after to | API, E2E | see FR-026-F | clear message, no results, no crash; applies to every date filter column too (FR-031) |

## 5. Questions and ambiguities

| # | Status | Question |
|---|---|---|
| Q1 | NEEDS-CLARIFICATION | Is the username case-sensitive? Are leading/trailing spaces trimmed? |
| Q2 | NEEDS-CLARIFICATION | After sign-in, does the admin return to the page they originally tried to open, or always the home screen? |
| Q3 | NEEDS-CLARIFICATION | If the idle sign-out audit entry cannot be saved, is the session still ended? (FR-023 says the change must not be saved if the entry cannot be saved.) |
| Q4 | NEEDS-CLARIFICATION | Maximum password length and any complexity rules beyond 8 characters. |
| Q5 | NEEDS-CLARIFICATION | Does the 5-minute pause end at exactly 5:00 after the 5th failure? Is the time counted from the 5th failure or the last attempt? |
| Q6 | NEEDS-CLARIFICATION | Do attempts during the pause extend it, and are they audited each time? |
| Q7 | NEEDS-CLARIFICATION | Does the developer reset also clear a sign-in pause and failure counter? |
| Q8 | NEEDS-CLARIFICATION | Academic year name format (must it look like `2026-27`?) and maximum length. |
| Q9 | NEEDS-CLARIFICATION | Are unique names (year, standard, category code, division) compared case-insensitively and after trimming? |
| Q10 | NEEDS-CLARIFICATION | Should the backend refuse data endpoints while no current year exists, or is the block UI-only? (Assumptions say web app only.) How is the UI block tested for the API? |
| Q11 | NEEDS-CLARIFICATION | May a stream be set on standards 1 to 10? Is `11` Science and `11` Commerce two rows (conflicts with FR-015 unique names)? |
| Q12 | NEEDS-CLARIFICATION | Valid range of display order (0? negative? maximum)? Must it be unique? |
| Q13 | NEEDS-CLARIFICATION | Tie-break when display order is equal. |
| Q14 | NEEDS-CLARIFICATION | See Q11: how do standards `11` and `12` with two streams coexist under unique names? |
| Q15 | NEEDS-CLARIFICATION | Division name allowed characters, maximum length, case rule. |
| Q16 | NEEDS-CLARIFICATION | Maximum lengths for category code and name; allowed characters. |
| Q17 | NEEDS-CLARIFICATION | In F0 no record references a division or category. How should "in use" be verified for them? Is a test-only reference acceptable? Can a division with children exist? |
| Q18 | NEEDS-CLARIFICATION | Does FR-046 apply to Delete and Mark-inactive actions on a stale record? |
| Q19 | NEEDS-CLARIFICATION | When a save changes nothing, is an Edit audit entry written? |
| Q20 | NEEDS-CLARIFICATION | Do old/new values hold the whole record or only changed fields? |
| Q21 | NEEDS-CLARIFICATION | For a failed sign-in with an unknown username, what is stored as user, and is the typed username stored? |
| Q22 | NEEDS-CLARIFICATION | Masking when a value is 4 characters or shorter (last-4 would show everything). |
| Q23 | NEEDS-CLARIFICATION | Maximum length of an audit reason. |
| Q24 | NEEDS-CLARIFICATION | Are area and action fixed lists, and is an unknown value rejected? Exact names for "inactive change" action (Edit?) and for an idle sign-out (Login with detail?). |
| Q25 | NEEDS-CLARIFICATION | Should the shell year refresh when midnight passes with a screen open? |
| Q26 | NEEDS-CLARIFICATION | Invalid page/size: reject or clamp? |
| Q27 | NEEDS-CLARIFICATION | Which columns count as "visible text columns" for search (dates, numbers, booleans, hidden)? |
| Q28 | NEEDS-CLARIFICATION | Null ordering and Gujarati collation for sort. |
| Q29 | NEEDS-CLARIFICATION | Is CSRF protection required? |
| Q30 | NEEDS-CLARIFICATION | Is HTTPS expected on the school network (cookie Secure flag)? |
| Q31 | NEEDS-CLARIFICATION | Which audit-log columns are searchable/filterable and which are in the export? Is the old/new values column exported in full? |
| Q32 | NEEDS-CLARIFICATION | Does Logout write an audit entry (FR-022 says every sign-out), and does Change password end other sessions? |
| Q33 | NEEDS-CLARIFICATION | The user's "full name" is shown in the shell; is it editable? (Spec has no profile screen.) |
| Q34 | NEEDS-CLARIFICATION | The spec text says seed order and statuses; do seeded categories have a display order / active flag, and can seeded ones be deleted when unreferenced? |

## 6. Counts

- FR rows: 142 across 46 FR ids (FR-001..FR-046, each with N, B, F cases; some table-level extras)
- SC rows: 11
- Cross-cutting rows: 16
- Edge-case rows: 8
- Questions: 34
