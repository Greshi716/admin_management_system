# Fixes after the diff review: 002-students

Source: `review-diff-002-students-2026-10-26-2008.md` (Result: FIX FIRST). Fix round 1 of 3 (second pass added M4, M5, M9, M10).

| Id | What changed | Commit |
|---|---|---|
| B1 (FR-005) | The edit screen now shows the stored photo and lets the admin add, replace and remove it (immediate upload and delete). Unit tests changed (the old test asserted "no photo on edit") and a Playwright test added (`students-edit.spec.ts`, add, replace, remove, checked against the API). | `fix B1` |
| S1 (FR-016) | `scripts/check-fr-coverage.sh` still checks the 46 F0 requirements, and on a feature branch also checks every FR of `specs/<branch>/spec.md` against the test files the branch added or changed against `main`. Now reports `002-students: 19 of 19`. Tags did not need to change. | `fix S1` |
| S3 (FR-019) | `StudentService.history` replaces standard, division, category and year ids by their names (unknown ids stay as numbers). The stored audit entry is unchanged. New test in `StudentProfileIT`. | `fix S3` |
| S4 (FR-019) | The History Playwright test edits through `PUT /api/students/{id}`; the `edit-address` test endpoint and its stale comment are removed. | `fix S4` |
| S5 | `any` removed from the new frontend specs and e2e helpers (typed views / `StudentInput`). | `fix S5` |
| M4 (FR-008) | A search typed as a whole mobile number with +91, a leading 0, spaces or dashes is turned into the stored 10 digits (`StudentListRequest.searchText`). Other search text is untouched. Test in `StudentListIT`. | `fix M4` |
| M5 (FR-008) | New `GET /api/student-filter-options` (in the contract) returns every standard, category and division of any year, active or not; the list filters use it, and division options show the year, for example "Standard 5 A (2025-26)". Tests in `StudentApiIT` and `student-columns.spec.ts`. | `fix M5` |
| M10 (FR-007) | The profile heading shows the record of the current academic year, else the most recent (as the list does). Unit test added. | `fix M10` |
| M9 (FR-016) | `ModuleDependencyTest` now reads every `com.admin.management.*` reference (imports and fully qualified names, not the package line). New self-test; the real sources have no violation. | `fix M9` |
| M11 (FR-005) | The photo is cached `immutable` for a year only when `v` is the current photo version; otherwise `no-cache`. Test added to `StudentPhotoIT`. | `fix M11, M12` |
| M12 | `StudentRegistrationIT` drops the audit-failure trigger in `@AfterEach`. | `fix M11, M12` |

## Not fixed

| Id | Reason |
|---|---|
| S2 | Left as it is on purpose: the owner asked in this session for the quickstart to be run on a seeded database instead of by hand (T026). The results file says so. B1 shows the owner may still want one look in the browser; the human stopwatch checks of T025 stay open. |
| M1, M2 | Touch-target sizes were estimated, not measured. Needs a design decision on the control token and a measurement in a browser; not changed blind. |
| M3 | Needs a check of Tomcat's behaviour with a body much larger than 5 MB; to be tested separately. |
| M6 | Nothing to change: `TestProfileGuard` already refuses to start the `test` profile unless the database name ends in `_test`, and it has a test (`TestProfileGuardTest`). The endpoints stay in the jar, as in F0. |
| M7, M8 | Branch history only (an F0 report and an F0 test fix sit in F1-labelled commits). Rewriting history would need a force-push, which is not allowed; nothing to change in the code. |
