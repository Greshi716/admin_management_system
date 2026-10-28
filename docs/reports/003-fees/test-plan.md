Result: PLAN READY

# Test plan: 003-fees (F2, fee structure and student fee accounts)

Source: `specs/003-fees/spec.md`, `plan.md`, `data-model.md`, `contracts/openapi.yaml`, `quickstart.md`, agreement v1.1 (4.2, 4.4, 5.1), `docs/decision-log.md` (rows 2026-10-25 and 2026-10-26 on fees, and 2026-10-28 on the Fee state column), constitution 1.1.0. Planned without reading implementation code.

Levels: UNIT, INT (integration, DB), API (contract), E2E (browser), MAN (manual), PERF.
Synthetic data only. Students are `Test Student A` .. `Test Student J`, GR numbers `T-0001`.., years such as `2026-27`, standards Std 6, Std 9, Std 10, categories O/S, STAFF, CEW. Amounts are `BigDecimal` in tests (never double); compare with `compareTo`, and assert scale 2 where stated. Time zone Asia/Kolkata, dates shown dd-MM-yyyy, API dates ISO (yyyy-MM-dd). A controllable clock is needed where "today" matters.

Reference fixture "S1" (used in many rows): year 2026-27 (current). Rows: R1 Std 10, blank category, 36,000.00, reg 500.00, due 10-06-2026; R2 Std 10 / O/S, 40,000.00, no reg fee; R3 Std 9, blank, 30,000.00, no reg fee. Students: A Std 10 O/S, B Std 10 STAFF, C Std 9 O/S, D Std 9 O/S, E Std 10 CEW.
Expected after finalise: A yearFee 40,000.00, reg 500.00 (from R1, fallback), total fee 40,500.00; B and E 36,000.00 + 500.00 = 36,500.00; C and D 30,000.00, reg none, total 30,000.00.

## 1. Functional requirement cases

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| FR-001-N | Add a row to a Draft year | API, E2E | year with no rows / POST row Std 9, blank category, 30,000.00, due date / row listed | 201; row has amount 30000.00 (scale 2); GET structure status `Draft`, version not null; screen shows "30,000.00" and tag Draft |
| FR-001-N2 | Category row and blank row coexist for one standard | API | R1 exists / add R2 Std 10 O/S / saved | 201; two rows for Std 10; no duplicate error |
| FR-001-B1 | Amount zero and registration fee omitted | API | Draft year / POST amount 0.00, no registrationFee, no dueDate | 201; amount 0.00; registrationFee null; dueDate null |
| FR-001-B2 | Two decimal places exactly | API | Draft / POST amount 28000.5 and 0.01 | 201 each; stored 28000.50 and 0.01 |
| FR-001-B3 | Largest amount the column holds | API | Draft / POST amount 9999999999.99, then 10000000000.00 | first 201 with 9999999999.99; second refused with field error on amount, never 500 (limit message NEEDS-CLARIFICATION Q6) |
| FR-001-B4 | Same standard in different years | API | R3 in 2026-27 / add Std 9 blank in 2027-28 | 201 (uniqueness is per year) |
| FR-001-B5 | Same standard and category, one has category and one blank | API | R2 and R1 exist / edit R2 to blank category | 409 duplicate with field error on categoryId (blank-category row would be second for Std 10) |
| FR-001-F1 | Second blank-category row for same standard | API, E2E | R3 exists / add another Std 9 blank | 409, field error beside standardId/categoryId; nothing saved; 1 row for Std 9 |
| FR-001-F2 | Duplicate standard + category | API | R2 exists / add Std 10 O/S again | 409 with field error; row count unchanged |
| FR-001-F3 | Missing standard or amount | API | Draft / POST without standardId; POST without amount | 422 each with field errors `standardId`, `amount` |
| FR-001-F4 | Inactive standard or category not offered, not accepted | API | Std 6 inactive, category CASUAL inactive / GET choices; POST row with each | choices exclude them; POST refused 422 with field error; an existing row holding the inactive standard still appears in choices and can be edited |
| FR-001-F5 | Row API for unknown year or standard | API | no such year 99999 / GET, POST | 404 for the year; unknown standardId gives 422, not 500 |
| FR-001-F6 | Not signed in | API | no session / GET and POST structure | 401, no fee data in body |
| FR-002-N | Edit and delete a Draft row without warning | API, E2E | Draft with R1 / PUT amount 37,000.00; DELETE R1 with confirm dialog | PUT 200 amount 37000.00, no confirm param needed; DELETE 204; delete asks a confirmation on screen; no revise warning appears |
| FR-002-N2 | Finalise with confirmation | API, E2E | S1 Draft with 3 rows / POST finalise with version, confirm on screen | 200, status `Finalised`, finalised_at set; screen shows tag Finalised |
| FR-002-B1 | Finalise with exactly one row | API | Draft, 1 row / finalise | 200 Finalised |
| FR-002-B2 | Delete the last Draft row, then finalise | API | Draft with 1 row / delete it, finalise | delete 204; finalise 422 with message (no rows) |
| FR-002-F1 | Finalise with no rows | API, E2E | year with no structure / press Finalise | 422 with message beside the button; status stays Draft; no audit entry |
| FR-002-F2 | Finalise twice | API | Finalised year / POST finalise again | refused 422 (contract) or 409; status and accounts unchanged; no second audit entry |
| FR-002-F3 | Cancel at the finalise confirmation | E2E | Draft with rows / press Finalise, cancel | no request sent; still Draft |
| FR-002-F4 | Finalise with stale version | API | window A edited a row after window B loaded / B posts finalise with old version | 409 STALE_VERSION with latest structure; still Draft |
| FR-003-N | Rows read-only after finalise on the screen | E2E | Finalised year / open screen | Add, Edit, Delete controls absent; Revise and Finalised tag shown |
| FR-003-B | Server refuses ordinary change with no confirm | API | Finalised / POST, PUT, DELETE without confirm | 409 REVISE_CONFIRMATION_REQUIRED, `current.studentCount` = 5 on S1; rows and accounts unchanged |
| FR-003-F | Direct API change of a finalised row | API | Finalised / PUT row with confirm absent and right versions | refused as above; nothing saved; no audit entry |
| FR-004-N | Revise one row: warn, confirm, recalc | E2E, API | S1 finalised / Revise, change R1 to 37,000.00, Save, confirm | warning text says every student's fee for the year will be recalculated and shows "5 students"; after confirm B and E show 37,000.00 + 500.00 = 37,500.00; A unchanged 40,500.00; one audit entry |
| FR-004-N2 | Second row change repeats warning | E2E | after FR-004-N / change R3 to 31,000.00 | warning shown again with 5; C and D show 31,000.00; second audit entry |
| FR-004-N3 | Revise add a row | API | S1 finalised, student F registered Std 6 (Fee not set) / add Std 6 blank 20,000.00 with confirm | 201; F fee becomes 20,000.00; entry action Create, reason Revise |
| FR-004-N4 | Revise delete a row | API | S1 finalised / delete R3 with confirm | 204; C and D "Fee not set"; entry action Delete, reason Revise, studentsRecalculated 5 |
| FR-004-B1 | Count includes a Left student | API | S1 plus student G marked Left with class record in 2026-27 / attempt revise without confirm | `studentCount` = 6 (spec assumption) |
| FR-004-B2 | Count excludes other years | API | class record of F in 2025-26 / revise 2026-27 | studentCount counts only 2026-27 records |
| FR-004-B3 | Revise deletes category row, fallback applies | API | S1 / delete R2 with confirm | A now 36,000.00 + 500.00 from R1 |
| FR-004-B4 | Revise on a year with zero class records | API | Finalised year, no students / revise a row | warning count 0; save succeeds after confirm; entry has studentsRecalculated 0 |
| FR-004-B5 | Revise that changes nothing | API | S1 / PUT R1 with identical values and confirm | NEEDS-CLARIFICATION Q9 (warning, entry, accountsChanged = 0?) |
| FR-004-F1 | Cancel at the warning | E2E | S1 finalised / Revise, edit R1, Save, Cancel | row amount still 36,000.00; no audit entry; all accounts unchanged |
| FR-004-F2 | Confirm true on a stale row | API | row changed in another window / PUT with old row version and confirm=true | 409 STALE_VERSION with latest structure; stale check comes before the revise warning |
| FR-004-F3 | Revise causes duplicate | API | S1 / change R2 to blank category with confirm | 409 duplicate; no recalculation; no audit entry |
| FR-004-F4 | Failure leaves nothing half done | INT | force exception after row update, before recalculation (test hook) | whole transaction rolled back: row old value, accounts old values, no audit entry |
| FR-004-F5 | Receipts untouched by revise | INT | NEEDS-CLARIFICATION Q10 (F3 not built: no receipts exist) | verify no table with receipts is written; carried to F3 plan |
| FR-005-N1 | Category row wins | UNIT | rows R1, R2; record Std 10 O/S | yearFee 40,000.00, due date from R2 |
| FR-005-N2 | Fallback to blank row | UNIT | same rows; record Std 10 STAFF | yearFee 36,000.00, reg 500.00, due date 10-06-2026 from R1 |
| FR-005-N3 | Registration fee from the blank row when category row has none | UNIT | R1 reg 500, R2 reg null; record Std 10 O/S | reg fee 500.00 (clarification 2026-10-26) |
| FR-005-N4 | Registration fee on the category row wins | UNIT | R2 reg 700.00, R1 reg 500.00; Std 10 O/S | reg 700.00 |
| FR-005-B1 | Neither row has a registration fee | UNIT | R2 and R1 both reg null; Std 10 O/S | reg null, none charged; total fee = yearFee |
| FR-005-B2 | Registration fee row only for a category, student in another category | UNIT | only R2 (reg 700.00) for Std 10; record Std 10 STAFF | no row applies: Fee not set, no registration fee |
| FR-005-B3 | Registration fee 0.00 on the category row | UNIT | R2 reg 0.00, R1 reg 500.00 | NEEDS-CLARIFICATION Q4 (does 0.00 count as "has one") |
| FR-005-B4 | Detained student, second year in same standard | INT | student in Std 10 in 2026-27 and again 2027-28, both finalised with reg 500.00 | both years' accounts carry reg 500.00 |
| FR-005-F1 | Standard with no row | UNIT, INT | rows only for Std 10; record Std 6 | state FEE_NOT_SET; amount null; no amount invented (not 0.00) |
| FR-005-F2 | Category row exists but only for another category and no blank row | UNIT | only Std 10 O/S row; record Std 10 CEW | Fee not set |
| FR-005-F3 | No late fee, concession, per-student amount | API | any student / look for endpoints to set a per-student fee | none exist: PUT/POST on /students/{id}/fees returns 404 or 405 |
| FR-006-N | Accounts created on finalise | INT | S1 Draft with 5 students / finalise | 5 accounts; amounts as the S1 expectations; finalise entry studentsRecalculated 5 |
| FR-006-N2 | Register a student in a finalised year | API | S1 finalised / register student H Std 10 O/S | account exists at once: 40,000.00 / 500.00 / due date; same transaction |
| FR-006-N3 | Add a later class record to a finalised year | API | existing student, no 2026-27 record / create class record Std 9 STAFF | account 30,000.00 |
| FR-006-B1 | Finalise a year with zero class records | INT | S1 rows, no students / finalise | Finalised; 0 accounts; entry studentsRecalculated 0 |
| FR-006-B2 | accountsChanged counts only real changes | INT | S1 / revise R1 amount; C, D unaffected | entry studentsRecalculated 5, accountsChanged 2 (B and E) |
| FR-006-B3 | Draft year class record | API | S1 Draft / profile of A | state NOT_FINALISED; yearFee null; no fee_accounts row |
| FR-006-B4 | Concurrent register and finalise | INT | thread 1 finalising, thread 2 registering a student | no class record without an account in a finalised year (quickstart 22) |
| FR-006-F1 | Class record of an unfinalised year gets no fee | INT | Draft / read fee_accounts | 0 rows for the year |
| FR-006-F2 | Account creation fails, registration fails | INT | force failure creating account (test hook) | student not registered, no class record, no audit entry |
| FR-007-N | Enter brought forward with a note | API, E2E | student A, no BF / PUT amount 2500.00, note "Dues 2025-26" version null | 200; version 0; Fees block BF 2,500.00; totalDue 40,500.00 + 2,500.00 = 43,000.00; audit Create |
| FR-007-N2 | Change and clear | API | BF 2500.00 / PUT 1000.50; then PUT 0.00 and no note | 1000.50, total due 41,500.50; clear stores 0.00 and null note, row not deleted; two Edit entries |
| FR-007-B1 | Amount 0.00 on first entry | API | no BF row / PUT 0.00 | 200; no negative effect on total due; audit entry behaviour NEEDS-CLARIFICATION Q8 |
| FR-007-B2 | Note length 200 and 201 | API | BF / PUT note of 200 characters, then 201 | 200 accepted; 201 refused 422 with field error on note |
| FR-007-B3 | Gujarati note | API | PUT note "ગયા વર્ષની બાકી ફી" | saved and returned identical; audit does not contain the text |
| FR-007-B4 | Unaffected by finalise and revise | INT | BF 2500.00 / finalise, revise | BF still 2500.00; version unchanged |
| FR-007-B5 | BF with fee not set | API | student F Std 6 no fee / read fees | state FEE_NOT_SET; totalDue 2,500.00 (BF alone) |
| FR-007-F1 | Negative amount | API | PUT -1.00 | 422 field error on amount; nothing saved; no audit entry |
| FR-007-F2 | More than two decimals | API | PUT 100.123 | 422; not rounded to 100.12 |
| FR-007-F3 | Stale brought-forward save | API | window A saved version 0 to 1 / window B PUT version 0 | 409 STALE_VERSION with latest values; B's value not saved |
| FR-007-F4 | Unknown student | API | PUT /students/99999/brought-forward | 404 |
| FR-007-F5 | Note too long and text never in audit | INT | any BF note change | audit changes contain `noteChanged: true` and never the note string |
| FR-008-N | Division change in a finalised year | API, E2E | S1 finalised / change A's division only | 200; standard and category unchanged |
| FR-008-N2 | Dialog shows fixed fields and message | E2E | S1 finalised / open class record dialog | standard and category read-only with message that the year's fee structure is finalised; division editable; `feeLocked` true |
| FR-008-B1 | Draft year keeps F1 rules | API | S1 Draft / change A to Std 9 | 200 |
| FR-008-B2 | Year with no structure | API | year 2027-28 without status row / change category | 200 |
| FR-008-B3 | Lock applies per year | API | 2026-27 finalised, 2027-28 Draft / change A's 2027-28 standard | 200 for 2027-28; 409 for 2026-27 |
| FR-008-B4 | Same standard and category re-sent | API | finalised / PUT enrollment with unchanged standard and category and new division | 200 (only a difference is refused) |
| FR-008-B5 | Left student | API | student G Left in finalised year / change category | 409 FEE_STRUCTURE_FINALISED |
| FR-008-F1 | Standard change in finalised year | API | S1 finalised / PUT standard Std 9 | 409 code FEE_STRUCTURE_FINALISED, message names the finalised structure; class record and account unchanged; no audit entry |
| FR-008-F2 | Category change in finalised year | API | same / PUT category STAFF | 409 same code |
| FR-008-F3 | Standard and division both changed | API | same / PUT both | 409; division also unchanged (all or nothing) |
| FR-008-F4 | Stale version beats lock or lock beats stale | API | stale version and standard change together | NEEDS-CLARIFICATION Q11 for the order; assert one of the two codes, never 500 |
| FR-009-N | Fees block with fee | E2E, API | A in S1 with BF 2,500.00 / open profile | yearFee 40,000.00; registration fee 500.00; BF 2,500.00; total due 43,000.00; due date 10-06-2026 (R1 has the date; R2 has none, see Q5); no receipts, paid, pending, Collect fee shown |
| FR-009-N2 | Fee set without registration fee | API | C in S1 | registrationFee null; totalFee 30,000.00; totalDue 30,000.00 (BF 0.00) |
| FR-009-B1 | Fee not set text | E2E | F Std 6 / open profile | "Fee not set"; totalDue = BF alone; no amount invented |
| FR-009-B2 | Draft year text | E2E | S1 Draft / open profile | "Fee structure not finalised"; total due = BF alone |
| FR-009-B3 | Profile for another year (`?yearId`) | API | A has 2025-26 and 2026-27 records / GET fees?yearId=2025-26 | fee of that year's record; without yearId, the shown record's year (current, else most recent) |
| FR-009-B4 | Money display | E2E | amounts 28000 and 28000.5 | "28,000.00" and "28,000.50"; Indian digit grouping for 1,00,000.00 style values NEEDS-CLARIFICATION Q7 |
| FR-009-F1 | No student | API | GET /students/99999/fees | 404 |
| FR-009-F2 | Not signed in | API | no session / GET fees | 401 |
| FR-009-F3 | No receipts UI | E2E | profile | no element for receipts, paid, pending or Collect fee |
| FR-010-N | Total fee column | E2E, API | S1 finalised / open list | A 40,500.00; B and E 36,500.00; C and D 30,000.00; no paid, pending or fee-status columns; no Fees filter |
| FR-010-N2 | Fee state column (decision 2026-10-28) | E2E | list with fee-set, fee-not-set, draft-year students | fee set: Fee state blank; no amount: Fee state "Fee not set", Total fee blank; no account: "Not finalised", Total fee blank |
| FR-010-N3 | Excel export | INT | S1 / export list | Total fee cells are numeric with format 0.00 (A = 40500, sums correctly); Fee state column text as above; the Fee state text matches the screen |
| FR-010-B1 | Sort and filter on Total fee | API | S1 / sort asc, then filter "= 36500" | order 30,000.00, 30,000.00, 36,500.00, 36,500.00, 40,500.00; filter returns B and E; blank values sort consistently (last) |
| FR-010-B2 | Pagination with the column | API | 10 students, page size 5 | page 1 and 2 each 5 rows, totals of rows correct |
| FR-010-B3 | Zero fee | API | row amount 0.00, no reg fee | Total fee 0.00 (shown, not blank), Fee state blank |
| FR-010-B4 | List shows the same record as the profile | API | student with 2 years | Total fee uses the shown record's year |
| FR-010-F1 | Column absent for paid and pending | API | search request asks for a `paid` or `pending` column/sort | rejected or ignored, 4xx not 500; no such column in the response |
| FR-010-F2 | Not signed in | API | POST /students/search | 401 |
| FR-011-N | Two decimals everywhere | INT | save 28000 and 28000.5 | DB scale 2 (`28000.00`, `28000.50`); API, screen, export all two places |
| FR-011-B1 | Smallest amount | API | 0.01 | accepted, stored 0.01 |
| FR-011-B2 | Trailing zero third decimal | API | amount 100.100 | NEEDS-CLARIFICATION Q3 |
| FR-011-B3 | Totals exact | UNIT | 0.10 + 0.20 and 33333.33 + 0.01 + 0.01 | exactly 0.30 and 33333.35 (a double would fail); result scale 2 |
| FR-011-F1 | Negative | API | -0.01 on fee amount, registration fee and BF | 422 each, field error beside the field |
| FR-011-F2 | Three decimals | API | 100.125, 100.124, 0.005 | 422 each; none rounded (100.13 is not stored) |
| FR-011-F3 | Non-numeric | API | amount "abc", "1e3", "1,000" | 400/422 with field error, never 500 |
| FR-011-F4 | Money type scan | UNIT | scan fees and changed students sources and tests for `double`/`float` on money | none found |
| FR-012-N | Audit for every write | INT | S1 / create, edit, delete a Draft row, finalise, revise, BF change | entries in area Fees: Create, Edit, Delete (no reason), Finalise (`studentsRecalculated`, `accountsChanged`, `rows`), revise entries with reason `Revise`, BroughtForward entries; each with old/new full amounts |
| FR-012-N2 | Entity names | INT | as above | `FeeRow#id`, `FeeStructure#yearId`, `BroughtForward#studentId` |
| FR-012-B1 | First BF is Create, later Edit | INT | BF entered, changed | first action Create, then Edit |
| FR-012-B2 | Only changed fields | INT | edit only due date of a row | entry holds dueDate old/new only, not amount |
| FR-012-B3 | Audit rows visible in the log | E2E | audit screen | area filter offers Fees, action filter offers Finalise; amounts shown in full, e.g. 36,000.00 |
| FR-012-F1 | Audit failure rolls back | INT | force `AuditService.record` to throw during row edit, finalise, revise, BF save | each change not saved; row, status, accounts, BF unchanged |
| FR-012-F2 | No entry for refused change | INT | refused duplicate, negative amount, stale save, missing confirm, locked class change | audit count unchanged |
| FR-013-N | Screen placement and look | E2E | signed in with a current year / Setup, Fees and salary, Fee structure | card with year title, one help line, status tag, save per row, Finalise or Revise; controls at least 44 px; table scrolls sideways at phone width; accent `#6D4AE0` |
| FR-013-N2 | Year picker starts on current year | E2E | years 2026-27 current, 2027-28 | current year selected first; can open 2027-28 and enter rows |
| FR-013-B1 | Hidden prototype parts | E2E | Setup | no Copy from last year, Salary rules, Facilities and deductions, Leave types, Backup and restore, Excel import |
| FR-013-B2 | Choices endpoint | API | year with inactive standard on an existing row | choices contain active ones plus those used by this year's rows |
| FR-013-F1 | No current academic year | E2E, API | all years end before today / open fee screen | blocked as in F0, redirect to create a year; only Change password and Logout stay |
| FR-013-F2 | Two windows edit same row | E2E | A and B load row version 3 / A saves, B saves | B refused (STALE_VERSION), screen reloads with A's values |
| FR-013-F3 | Window B Draft while A finalised | E2E | A finalises / B edits a row | B refused before any revise warning; B reloads as Finalised |
| FR-013-F4 | Add after another window added | API | A and B loaded structureVersion 4 / A adds row / B adds another row with old structureVersion | 409 STALE_VERSION with latest structure |
| FR-013-F5 | Not signed in / session expired | E2E | idle sign-out / press Save | redirected to sign-in; nothing saved |
| FR-014-N | Coverage check | MAN | run `scripts/verify` | FR-001 to FR-014 each referenced by at least one passing test |
| FR-014-F | Missing reference fails the check | MAN | temporarily remove an FR reference (on a scratch copy) | the strict FR coverage check fails and names the FR |

## 2. Success criteria

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| SC-001 | 12 standards in under 10 minutes | MAN | admin who used the screen once / enter 12 rows with due dates and finalise | under 10 minutes by stopwatch; result recorded; 12 rows finalise OK |
| SC-001-API | 12-standard structure finalises | API | 12 rows / finalise | 200, 12 rows listed (functional part of the same case) |
| SC-002 | Fee selection is right for every case | UNIT, INT | S1 plus Std 6 student and registration-fee standard | category row, fallback row, no row, registration-fee standard each 100% correct; none shows an invented amount (no 0.00 for Fee not set) |
| SC-003 | Revise warning correct and nothing changes before confirm | E2E, INT | 5 row changes in a row on S1 | each warning shows the right count (5, then 5, after a student registered 6); before confirm all accounts equal old values; after, every record equals the new structure (re-derived independently from rows) |
| SC-004 | Performance 2,000 class records | PERF | 2,000 synthetic class records / finalise, then confirmed revise of one row | each under 10 s on the dev PC (assertion message carries the measured time); target-PC check carried to F11 (MAN, noted) |
| SC-004-B | List on 10,000 students | PERF | 10,000 students with Total fee column | list under 2 s (plan goal) |
| SC-005 | Lock refused and explained | E2E, API | 20 combinations (standard, category, both, divisions) in a finalised year | all standard/category changes refused (409), division-only allowed; dialog explains before submit |
| SC-006 | Two-decimal storage and display | INT, E2E | valid: 0.00, 0.01, 28000, 28000.5; invalid: -1, 100.123, empty, text | valid stored/shown with exactly two decimals; invalid refused with field message |
| SC-007 | Every change has an audit entry | INT | scripted run of N changes and M failed attempts | audit entries in area Fees = N exactly; zero for the M failures |
| SC-008 | Every FR covered | MAN | `scripts/verify` coverage output | all 14 FRs covered and passing |

## 3. Cross-cutting cases

| ID | Title | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| X-MONEY-1 | Column types | INT | migration V7 applied | `fee_rows.amount`, `registration_fee`, `fee_accounts.amount`, `registration_fee`, `fee_brought_forward.amount` are NUMERIC(12,2); CHECK >= 0 rejects -0.01 even by direct SQL |
| X-MONEY-2 | Java types | UNIT | reflection/scan on fees entities and DTOs | all money fields are `BigDecimal` |
| X-MONEY-3 | JSON precision | API | amount 0.1 and 0.07 in request JSON | stored 0.10 and 0.07 (no binary float artefacts such as 0.07000000000000001) |
| X-MONEY-4 | Rounding rule not used silently | UNIT | any fee total | totals are additions only; no ROUND applied; HALF_UP only if a later rule adds it (Excel ROUND(2.5,0)=3 reference noted for F3) |
| X-AUDIT-1 | Same transaction | INT | each write path with a forced audit failure | see FR-012-F1: fee and audit succeed or fail together |
| X-AUDIT-2 | Audit has actor and time | INT | any write | entry has user `admin`, time in Asia/Kolkata offset, area Fees |
| X-AUDIT-3 | Masking of other fields | INT | a student change in the same test | Aadhaar still masked (last 4); fee amounts not masked |
| X-LOCK-1 | Optimistic locking on row | API | two PUTs with same row version | first 200, second 409 STALE_VERSION with latest row |
| X-LOCK-2 | Optimistic locking on structure | API | two requests with same structureVersion | first succeeds; second 409 |
| X-LOCK-3 | Optimistic locking on BF | API | see FR-007-F3 | 409 |
| X-LOCK-4 | Pessimistic locks do not deadlock | INT | 20 parallel registrations during one finalise and one revise | all complete; no deadlock error; no orphan class records |
| X-DATE-1 | Due date display | E2E | due date 2026-06-10 | shown as 10-06-2026 on screen, profile, export; API ISO |
| X-DATE-2 | Date entry | E2E | type due date 31-04-2026 (invalid) and 29-02-2028 (valid leap) | invalid refused beside the field; valid accepted |
| X-DATE-3 | Year boundaries | API | academic year 01-04-2026 to 31-03-2027; clock at 2027-03-31 23:30 IST and 2027-04-01 00:30 IST | current year switches at midnight Asia/Kolkata, not UTC; fee screen opens on the new year (or is blocked with no year) |
| X-DATE-4 | Due date outside the year | API | due date 2030-01-01 for 2026-27 | NEEDS-CLARIFICATION Q5 |
| X-GUJ-1 | Gujarati in note and names | E2E | BF note "ગયા વર્ષની બાકી ફી"; student name in English | stored and shown unchanged; no mojibake in screen, audit, export; export file opens with correct characters |
| X-GUJ-2 | Gujarati digits | E2E | amount typed "૨૮૦૦૦" | refused beside the field (or NEEDS-CLARIFICATION Q7), never stored as another value |
| X-PERM-1 | Every fee endpoint needs sign-in | API | no session / call each path in openapi.yaml | 401 on all, body without data |
| X-PERM-2 | CSRF | API | signed in, mutation without CSRF token | refused 403 as in F0; nothing saved |
| X-PERM-3 | Test-support controller absent in production profile | INT | default (non-test) profile | `/api/test-support` fee seeding endpoints return 404/401 |
| X-LOG-1 | No secrets or personal data in logs | INT | run a full scenario, capture app log | no password, session id, CSRF token, Aadhaar, BF note text, or student names in log lines |
| X-LOG-2 | Error body hygiene | API | force a server error | body has no stack trace or SQL |
| X-MOD-1 | Module dependencies | UNIT | `ModuleDependencyTest` | fees -> students.api, masterdata.api, audit, shared; students -> shared only; no cycle; allowed cross-module table reads are exactly `audit_log`->`users` and `students`->`fee_accounts` |
| X-MIG-1 | Migration | INT | empty DB and F1 DB | V7 applies; V1 to V6 checksums unchanged |
| X-FK-1 | In-use master data | API | standard Std 10 used by a fee row / delete it | refused (F0 FR-020); marking inactive allowed; same for category and academic year |
| X-NODEL-1 | No delete path | API | DELETE on accounts and brought-forward | 404 or 405; no student or class record is deleted by any fee action |
| X-EXCEL-1 | Other exports unaffected | INT | audit and master data exports | amounts and numbers still read correctly after the shared exporter change; BigDecimal numeric cell with 0.00; integer cells not truncated |
| X-ERR-1 | Error shape | API | each 409/422 | body is Problem with `code`, `field` where relevant; `REVISE_CONFIRMATION_REQUIRED` `current` = `{studentCount: n}` |

## 4. Spec edge cases (each its own row)

| ID | Edge case | Level | Given / When / Then | Expected result |
|---|---|---|---|---|
| EC-1 | No current academic year | E2E | no year covers today / open Fee structure | blocked as F0; admin sent to create a year; Logout and Change password available |
| EC-2 | Draft year shows "Fee structure not finalised" | E2E, API | S1 Draft / profile and list | "Fee structure not finalised" on profile; list Fee state "Not finalised"; no amount |
| EC-3 | Student registered after finalise, standard without row, then row added through Revise | API | S1 finalised / register Std 6 student F ("Fee not set"); revise add Std 6 blank 20,000.00 | before: Fee not set; after confirm: 20,000.00; warning count 6 |
| EC-4 | Standard, category or year used by a row cannot be deleted; inactive not offered | API | as X-FK-1 and FR-001-F4 | delete refused; inactive hidden from new rows, kept on existing |
| EC-5 | Left student keeps account and is counted | API | student G marked Left, S1 finalised / revise | account stays, recalculated; count includes G |
| EC-6 | Two windows, including finalise vs Draft | E2E | FR-013-F2 and FR-013-F3 | second save refused; screen reloads |
| EC-7 | Wrong standard or category after finalise cannot be corrected | API, E2E | finalised / change standard | 409 FEE_STRUCTURE_FINALISED; no unlock endpoint or button anywhere (no route found) |
| EC-8 | Revise deletes a used row | API | S1 / delete R2 and then R1 and R3 | A falls back to R1 after R2 delete; after all rows deleted every student Fee not set (deleting last row of a Finalised year: Q2) |
| EC-9 | Revise lowers fee below paid | MAN | F3 not built | NEEDS-CLARIFICATION Q10; carried to F3 plan: "excess paid" shown; F2 only checks no refund logic and no payment field exist |

## 5. Questions on the spec (NEEDS-CLARIFICATION)

Where tests above depend on one, the expected result is not asserted until answered.

1. **Q1 Spec conflict on the list column.** FR-010 and US2-3 say the Total fee column shows "Fee not set" or "Not finalised". The decision log of 2026-10-28 says a separate Fee state column shows those texts and Total fee stays blank. This plan follows the decision log. Please update FR-010 and US2 scenario 3 text.
2. **Q2 Delete the last row of a Finalised year through Revise.** Allowed (all students Fee not set), or refused? Does the year stay Finalised?
3. **Q3 Amount "100.100"** (three decimals, numerically two). Accepted or refused? And amounts typed with commas ("28,000.50") or Gujarati digits.
4. **Q4 Registration fee 0.00 versus blank.** Does 0.00 on the category row count as "has a registration fee" (blocking the fallback to the blank row)?
5. **Q5 Due date.** Must it lie inside the academic year? Which due date does a student get when the category row has no date but the blank row has? (Spec: "from the row the amount came from", so none.) Confirm.
6. **Q6 Maximum amount.** Limit is the column size (9,999,999,999.99). Should a business maximum exist and what message is shown?
7. **Q7 Digit grouping.** Plan says Indian grouping (1,00,000.00) while the spec examples show 28,000.00 (same up to 99,999.99). Which is required above 1 lakh?
8. **Q8 Brought-forward 0.00 on first save.** Does it write a Create audit entry? Does saving unchanged values write an entry?
9. **Q9 Revise with no real change.** Is the warning shown and an audit entry written when nothing differs?
10. **Q10 Receipts and excess paid (FR-004).** Not testable until F3. Confirm F2 acceptance is only "no receipt table is touched" and that excess paid is tested in F3.
11. **Q11 Order of checks** when a stale version and a locked standard change arrive together (which code wins).
12. **Q12 Finalise of an already Finalised year**: contract says 422, spec silent on the message and code.
13. **Q13 Finalise a past or future year.** Assumption says any year allowed; confirm that there is no check on the year dates.
14. **Q14 Brought-forward note** whitespace-only: stored as blank note? Is the 200 limit counted in characters (Gujarati combining characters count as several)?
15. **Q15 List "shown record" when the student has no record for the current year** and the most recent year is a past finalised one: Fee state and Total fee come from that past record, as the profile does?

## 6. Test data and environment

- Fixtures created through the test-support helpers (`/api/test-support`, test profile, database name ends `_test`): S1; 2,000 and 10,000 synthetic students for PERF.
- Every test class carries `@Covers("FR-xxx")`; Playwright titles carry the FR id; this plan's IDs (FR-xxx-N/B/F) can be used as suffixes.
- No real names, Aadhaar, receipts or salaries; passwords are generated fixtures.

## Counts

- FR cases: 14 FRs, each with normal, boundary and failure cases (rows in section 1).
- Success criteria rows: 8 (plus 2 sub-rows).
- Cross-cutting rows: see section 3. Edge case rows: 9. Questions: 15.
