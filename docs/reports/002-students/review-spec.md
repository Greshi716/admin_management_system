# Spec review: 002-students (second review)

Reviewed: `specs/002-students/spec.md` (line numbers below refer to it), `checklists/requirements.md`, agreement v1.1 (24-10-2026), `docs/decision-log.md` (now with the Students row of 25-10-2026, line 29), constitution 1.0.1, CLAUDE.md, `docs/design/ui-tokens.md`.
Date: 2026-10-25. The reviewer changed no file except this report. This replaces the first review.

## Verdict: READY WITH FIXES

All four earlier blocking findings are resolved. No blocking finding remains. Six should-fix items and some wording items remain; none stops planning, but S1 and S2 should be settled before tasks are written.

Counts: Blocking 0, Should fix 6, Minor 7, Questions 3.

## Status of the first review

| First review | Now | Where |
|---|---|---|
| B1 audit area Students | Resolved in the decision log (line 29). The agreement's area list (agreement line 621) is not updated yet; see S1. | FR-012 (line 154) |
| B2 category invented rule | Resolved by clarification | FR-006 (line 138), Clarifications (line 19) |
| B3 FR-009 versus FR-015 | Resolved; both now describe a plain Mark as left, no prepare-LC | lines 145, 160 |
| B4 status and no-current-record in the list | Resolved | FR-008 (line 144), US2 scenario 5 |
| S1 Aadhaar and religion/caste | Resolved by clarification and decision (FR-010, line 146); one gap remains, see S3 | |
| S2 mobile shown masked on profile | Not addressed; see S2 | |
| S3 invented validation rules | Mostly resolved: FR-017 (line 134) lists them, future dates refused by owner decision; the rest are documented assumptions | lines 192 to 194 |
| S4 past-year edits | Still an assumption, now with a pointer to F3 (line 195); see M1 | |
| S5 stale edit had no FR | Resolved: FR-018 (line 140), SC-009 | |
| S6 GR No. correction | Resolved by clarification; one edge case remains, see Q1 | |
| S7 FR-001 too big | Partly: it now includes standard and division; still one long requirement; see M2 | |
| S8 FR-007 mixed | Not changed; see M2 | |
| S9 photo FR | Resolved: FR-005 (line 133) | |
| S10 to S12 | Resolved: every FR and SC has a story and agreement or FR tag; SC-003 uses 10,000 rows and names the target PC; US4 scenarios 4 and 5 have a When; "sensible order" and "short confirmation" are replaced | |

## Blocking

None.

## Should fix

**S1. Agreement and decision log disagree on audit areas.**
Location: decision log line 29 ("areas so far: Security, Master data, Audit log, Students"); decision log line 24 (audit-log exports go under Security, "replaces the earlier 'Audit log' area"); agreement line 621 (Security / Master data / Fees / Payroll / Fund).
Problem: the new row names an area "Audit log" that row 24 removed, and the agreement's field list does not yet include Students. F0 recorded the same kind of change in agreement section 11.
Fix: Correct the list in line 29 (Security, Master data, Students) and add Students to the agreement's area list and its section 11 change log.

**S2. Mobile numbers on the profile: shown in full or masked?**
Location: FR-009 (line 145); prototype Student profile image shows Mobile 1 as "98xxxxxx47"; agreement does not mark mobile sensitive (line 254).
Problem: the spec is silent; a tester cannot tell which is right, and search by mobile (FR-008) assumes it is findable.
Fix: Add one sentence to FR-009: mobile numbers are shown in full (or masked like the prototype).

**S3. Which category does the profile show?**
Location: US2 scenario 4 (line 66), FR-009; FR-006 (line 138) keeps a student category and a yearly category that can differ.
Problem: "category" on the profile and in the heading is ambiguous after the yearly category is changed; the student-level category is stored but no screen is said to show it.
Fix: State that the heading and class history show the yearly category, and say whether the student-level (admission) category is shown at all.

**S4. Where and how a class record is changed.**
Location: FR-009 (line 145: "a way to change each year's class record"); US3 scenario 2 (line 82).
Problem: no screen action, form fields or validation scenario is described, unlike Mark as left. Division choices (current year only, inactive hidden) matter for past years: a past year's divisions belong to that past year.
Fix: Say that the change form offers the standard, division (of that record's year) and category, and add one error scenario.

**S5. FR-012 does not list photo changes or class-record creation by registration.**
Location: FR-012 (line 154); FR-005 (line 133).
Problem: the audit list names student create, edit, status change and class record create and change; adding, replacing or removing a photo is not named. A photo change is an edit of the student.
Fix: Add "photo added, replaced or removed (without the image)" to FR-012.

**S6. Success criteria and plan documents now disagree on list size.**
Location: SC-003 (line 178, 10,000 students) against plan.md and quickstart.md (5,000).
Problem: plan files were written before the change.
Fix: Update the plan, research and quickstart to 10,000 (done in the plan re-run that follows this review).

## Minor

**M1.** Assumption line 195 leaves past-year edits open "until F3"; say who decides (the owner, when F3 is specified) and that the audit trail covers it.
**M2.** FR-001 (line 129) and FR-007 (line 139) each hold several behaviours; consider splitting into field list / defaults and history display / year status.
**M3.** "In small text" (FR-008, line 144) and "a message" (FR-011) are visual wording; fine for a spec, but the plan should fix the sizes.
**M4.** FR-009 asks for confirmation before Mark as left, a new UI rule; harmless, but record it (it is listed in US3 scenario 4 only).
**M5.** FR numbers are out of order in the document (FR-017 after FR-005, FR-018 after FR-007); same as F0, no action needed.
**M6.** `checklists/requirements.md` notes still describe open markers and the first-draft assumptions; refresh the notes.
**M7.** The decision log file holds its table twice (a second "CampusFlow decision log" header at line 42); not part of this spec, but it makes the new row easy to miss. Clean it up in its own commit.

## Questions for the owner

**Q1.** FR-002 forbids a GR No. the student "ever had". If the admin corrects a wrong number and then wants the original back, the original is refused. Should a student be allowed to return to a number only that same student had?
**Q2.** Should mobile numbers be masked on the profile as in the prototype image (S2)?
**Q3.** Should the student-level category (chosen at admission) be shown anywhere, or only stored (S3)?

## What checked out

- Scope: no F1b, F3, F4, F5 or Excel import content; the shell restyle is authorised by the decision log (line 27).
- No technology, table or class names; file types and sizes are user-facing limits.
- Every FR has MUST, a unique ID, a story tag and a source; every FR appears in a scenario or edge case (FR-016 is the testing rule; FR-013 to FR-015 are covered by US4 and US2).
- Constitution: no hard delete (FR-004), audit in the same transaction (FR-012), masked sensitive fields and no encryption requirement stated (FR-010, decision log line 29), tests per FR (FR-016), roll number absent.
- The two earlier NEEDS-CLARIFICATION markers are gone; no TBD or dangling clarification reference remains.
- Numbers agree inside the spec: 5 MB, 10 digits, 12 digits, 1280 px, 10,000 rows, 30 seconds.
