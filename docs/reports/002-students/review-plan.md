# Plan review: 002-students

Reviewed: `specs/002-students/` spec.md (current, 19 FRs, SC-001 to SC-010), plan.md, research.md (R1 to R12), data-model.md, quickstart.md, contracts/openapi.yaml; F0 plan and research for reused decisions; agreement v1.1 (section 4.3, audit area line 621); decision log (Students row, line 29); constitution 1.0.1; CLAUDE.md; `docs/design/ui-tokens.md`, `ui-prototype.md` and `frontend/src/styles.css`. I also read the F0 code the plan relies on (`ColumnDef`, `TableQueryService`, `AuditService`, `SensitiveMasker`, `ApiExceptionHandler`). Line numbers refer to the files as they are now.
Date: 2026-10-25. No file changed except this report.

## Verdict: READY WITH FIXES

The plan is short, reuses F0 properly and honours the owner's ten decisions. No finding blocks writing tasks. Eight should-fix items need an edit to the plan files first, and the most important one (S4) needs a spike as the first task.

Counts: Blocking 0, Should fix 8, Minor 9, Questions 3.

## 1. FR and SC coverage

All 19 FRs and 10 SCs are addressed. Where:

| FR | Plan, data model or contract |
|---|---|
| 001, 017 | `POST /students`, `students` columns and CHECKs, research R10 |
| 002 | `student_gr_numbers`, R3 |
| 003 | `GET /students/name-matches`, R6 |
| 004, 018 | `PUT /students/{id}`, `PUT .../left`, `@Version` (plan line 100 onward) |
| 005 | photo endpoints, `student_photos`, R1, R2 |
| 006, 007 | `student_enrollments`, `PUT .../enrollments/{yearId}`, `classHistory` |
| 008, 010 | `/students/search`, `/students/export`, R4, R5 |
| 009, 019 | `GET /students/{id}`, `GET /students/{id}/history`, R12 |
| 011 | frontend only, R9 |
| 012 | plan decisions 3 and 8, data-model "Audit" |
| 013, 014, 015 | R8, plan Project Structure (frontend), quickstart 15 and 16 |
| 016 | the F0 coverage script (plan line 49) |

Gaps, none blocking: FR-015 and FR-013 have no named automated check (S6); SC-001 and SC-006 give times but no scenario measures them (S6).

## 2. Constitution Check (plan.md lines 40 to 49)

| Principle | Evidence in the plan | Honest? |
|---|---|---|
| I | N/A, no money (line 42) | Yes |
| II | N/A; past-year edits flagged for F3 (line 43) | Yes |
| III | `AuditService.record` MANDATORY (line 44) | Mostly. It lists create, edit and status change but not photo changes or class-record creation (FR-012 now names photos). |
| IV | "No delete endpoint or repository delete is exposed" (line 45) | No: the contract has `DELETE /students/{id}/photo` and the data model deletes a `student_photos` row (data-model line 81). See S8. |
| V | One migration `V6`, `ddl-auto=validate` (line 46) | Yes, but V6 also needs an index on `audit_log` (S2) |
| VI | Masked in lists, exports, audit; never logged (line 47) | Yes; add `Cache-Control: no-store` on the edit endpoint (S7) |
| VII | New package; order `shared` <- `audit` <- `masterdata` <- `students` (line 48) | Yes, and a test guards it. The plan says `audit/` is unchanged (line 78) while R12 changes it (S1). |
| VIII | `@Covers` plus F0 script (line 49) | Yes |

## 3. Technology

No new library; Spring Boot 4.1.2, Angular 22.2, POI and Flyway are as F0 (verified there). Unverified guesses in this plan, all small: the multipart property `spring.servlet.multipart.max-file-size` on Boot 4.1 (M4); `byte[]`/`bytea` mapping in Hibernate with `ddl-auto=validate` (M3); ImageIO and phone-photo rotation (M2); the 30 KB per photo estimate (M8).

## 4. Data model against the agreement

- `students` (data-model lines 8 to 41) matches agreement 4.3 field by field; additions are marked. `category_id` and `admission_standard_id` are required links as in the agreement.
- `student_enrollments` (lines 53 to 69) matches the agreement's Yearly Class Record; `year_start_date` is an addition and is explained.
- Foreign keys are `ON DELETE RESTRICT`; no cascade (line 6). `audit_log` append-only trigger is untouched.
- Money: none.
- Mismatch to note: the agreement's `gender` is a "Choice" with no values; the model fixes three values (line 18). Documented in the spec as an assumption.
- Index claim: the owner asked for indexes for "name, GR No., mobile, standard, division, category"; lines 38 to 41 give them and honestly say substring search scans the table.

## 5. API contract

Every endpoint FR is present. Error model, auth, CSRF and paging are those of F0 by reference. Names are consistent with `ColumnDef` field names (`grNo`, `mobile1`). Problems: S3 (pick-lists), S7 (edit endpoint caching), M5 (422 versus 409), M10 (create-only fields in `StudentInput`).

## 6. Security

Session, CSRF, lock-out and secrets are F0's. New points: full Aadhaar leaves only through `GET /students/{id}/edit` (good) but without a no-store header (S7); upload checks by decoding, size and pixel limits (good, R2); validation messages must not echo values (R4 item 6, good); sensitive filter and sort refused (R4, good, and no other F0 list uses `asSensitive`, so nothing else changes).

## 7. Migrations

`V6__students.sql` is next after V5 on `main`; nothing edits V1 to V5. No seed data. Add the audit index (S2).

## 8. Testability

Backend integration tests per FR with synthetic data; quickstart has 19 scenarios; the test database is the F0 one. Gaps: S6. A 10,000-student fixture is a "test helper" (quickstart 17); F0's `TablePerformanceIT` exists to extend.

## 9. Risks and simpler options

S4 is the main risk. Other points in M-items.

## 10. Design

R8 follows the decision log (top bar, tabs, left list, 1280 px) and keeps the purple tokens, but does not name the colours (S5).

## Blocking

None.

## Should fix

**S1. Plan says the audit module is unchanged, but research changes it.**
Location: plan.md line 78 ("audit/ unchanged"); research.md R12 (line 74: new `AuditService.history`); plan.md line 44 (Principle III omits photo, history).
Problem: two documents disagree; the audit change is also not in the Source Code list or the test list.
Fix: List `audit/` as changed (new read method and its test) in plan lines 44 and 78.

**S2. The History query has no index.**
Location: research.md R12; F0 data-model audit_log indexes `(at)` and `(area, action, at)`.
Problem: reading entries by record id (`Student#12`, `StudentEnrollment#40`) filters on the `entity` column, which has no index, so each profile opens a scan of all Students-area entries (tens of thousands of rows at 10,000 students).
Fix: Add `CREATE INDEX ... ON audit_log (entity, at)` to `V6` (V5 shows an index-only migration on this table is acceptable) and note it in data-model.md.

**S3. Pick-lists give only the current year's divisions.**
Location: contracts/openapi.yaml `/student-pick-lists` (line 133); spec FR-006 (line 142: the change form offers divisions "for that record's year").
Problem: changing a past year's class record needs that year's divisions; the endpoint cannot return them. An existing inactive value must also stay selectable.
Fix: Add an optional `yearId` parameter and return the record's current values even if inactive.

**S4. List columns built from correlated subqueries are untested in the F0 table framework.**
Location: research.md R5 (line 39); `TableQueryService.fetch` selects every `ColumnDef` expression and also sorts and filters on it; F0 has only simple derived columns.
Problem: a nested "current else latest by `year_start_date`" subquery, run for three columns, plus a `CASE` sort built from the lookup maps, is the most complex piece of the feature and has not been tried with the page, count, filter, sort and export queries together. It must hold SC-003 (10,000 students, 2 s, on 4 GB RAM).
Fix: Make a spike with 10,000 rows and all five query paths the first task; name a fallback in the plan (for example, a read view owned by masterdata, as R5 already mentions, or showing only the current record's columns in SQL).

**S5. Top bar and tab colours are not named, and the shell tokens are not in the source list.**
Location: research.md R8 (line 55: "darkest token shade"); `docs/design/ui-tokens.md` (the top bar is "surface"); `frontend/src/styles.css` tokens; decision log line 27.
Problem: ui-tokens has no dark top-bar, active-tab or tab-text token. The decision says to use the darkest F0 purple or a dark shade of the same hue, so the plan must say which token (candidates in `styles.css`: `accent-deep` and `ink`), check text contrast, and list the new `@theme` tokens in Project Structure.
Fix: Name the tokens and the contrast check in R8 and add `styles.css` to the changed files.

**S6. No named test for the layout requirements and the timed success criteria.**
Location: quickstart.md scenarios 9 and 15; spec FR-013, FR-015, SC-001, SC-006.
Problem: matching the prototype and the 1280 px width are "look and see" checks; the 2-minute and 30-second criteria are not measured by any scenario.
Fix: State the Playwright assertions (tabs present and absent, computed content width at most 1280 px, hidden items absent from the profile and list) and say which timed criteria are checked by hand or by a timed run.

**S7. The edit endpoint returns full Aadhaar with no cache control.**
Location: contracts/openapi.yaml `/students/{id}/edit` (line 81); research R4 item 4.
Problem: a browser or proxy cache could keep the full values; nothing in the plan forbids it.
Fix: Require `Cache-Control: no-store` on `/students/{id}/edit` and add a test for the header.

**S8. Principle IV wording contradicts the contract.**
Location: plan.md line 45; contracts/openapi.yaml `DELETE /students/{id}/photo`; data-model.md line 81.
Problem: the plan says no delete endpoint exists; the photo can be removed by deleting a `student_photos` row.
Fix: Reword: no student, class record or GR number is ever deleted; the photo bytes may be removed, and that is audited.

## Minor

**M1.** plan.md line 97 still says the agreement's area list and the decision-log row need fixing; both are done now (agreement line 621, decision-log line 29). Update the text.
**M2.** R2 (line 14): I believe the JDK image reader ignores a phone photo's rotation tag, so portrait photos may appear sideways. Unsure; test with a phone photo and, if needed, read the tag.
**M3.** R1: the `bytea` mapping with Hibernate and `ddl-auto=validate` is not stated (a large-object mapping can fail validation). Name the mapping and check it in the first task.
**M4.** Plan line 80: confirm the multipart property names on Spring Boot 4.1.2.
**M5.** Contract `POST /students` returns 422 for a duplicate GR No.; F0 uses a `ConflictException` (409) carrying a field name for the overlap rule. Pick one for duplicates and use it in the form.
**M6.** R7 (line 50): the dependency test counts imports only, and "audit public classes" is not defined (`AuditEntry`, `AuditRepository` are also public). List the allowed classes.
**M7.** The Excel export: whether cell text such as "=1+1" typed into names or address is neutralised was not checked (unsure); the F0 exporter may already handle it.
**M8.** Photo sizes (30 KB, 300 MB at 10,000 students) are estimates; measure with real phone photos (synthetic ones) and note the effect on the daily backup.
**M9.** Contract `StudentInput.required` omits `standardId` and `divisionId` that create needs; the text explains it, but a schema reader will miss it (spec FR-001).

## Questions for the owner

**Q1.** Top bar colour: use the existing deep purple (`#4B2FA8`) with white text, or add one new darker token close to the prototype's look?
**Q2.** Religion/caste masked "last 4 visible" shows most of a short word (for example five letters shows four). Keep as decided, or mask short values fully?
**Q3.** The History block is capped at 200 entries per student. Is that enough, or should it page?

## What checked out

- No module cycle exists today (import scan) and the plan keeps it that way; no other F0 list uses `asSensitive`, so refusing filter and sort on sensitive columns affects only students.
- The sensitive-value handling matches F0's masking rules and FR-010; audit old and new values pass through `SensitiveMasker` at any depth, so the History block cannot show a full Aadhaar.
- The stale-save path reuses F0's `@Version` and 409 pattern; GR No. never-reuse is enforced by a primary key, not only by code.
- Migrations: next number correct, nothing edited, no cascade on people.
- Scope: nothing from F1b, F3, F4, F5.
