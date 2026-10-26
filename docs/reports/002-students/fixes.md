# Fixes after the diff review: 002-students

Source: `review-diff-002-students-2026-10-26-2008.md` (Result: FIX FIRST). Fix round 1 of 3.

| Id | What changed | Commit |
|---|---|---|
| B1 (FR-005) | The edit screen now shows the stored photo and lets the admin add, replace and remove it (immediate upload and delete). Unit tests changed (the old test asserted "no photo on edit") and a Playwright test added (`students-edit.spec.ts`, add, replace, remove, checked against the API). | `fix B1` |
| S1 (FR-016) | `scripts/check-fr-coverage.sh` still checks the 46 F0 requirements, and on a feature branch also checks every FR of `specs/<branch>/spec.md` against the test files the branch added or changed against `main`. Now reports `002-students: 19 of 19`. Tags did not need to change. | `fix S1` |
| S3 (FR-019) | `StudentService.history` replaces standard, division, category and year ids by their names (unknown ids stay as numbers). The stored audit entry is unchanged. New test in `StudentProfileIT`. | `fix S3` |
| S4 (FR-019) | The History Playwright test edits through `PUT /api/students/{id}`; the `edit-address` test endpoint and its stale comment are removed. | `fix S4` |
| S5 | `any` removed from the new frontend specs and e2e helpers (typed views / `StudentInput`). | `fix S5` |
| M11 (FR-005) | The photo is cached `immutable` for a year only when `v` is the current photo version; otherwise `no-cache`. Test added to `StudentPhotoIT`. | `fix M11, M12` |
| M12 | `StudentRegistrationIT` drops the audit-failure trigger in `@AfterEach`. | `fix M11, M12` |

## Not fixed

| Id | Reason |
|---|---|
| S2 | Left as it is on purpose: the owner asked in this session for the quickstart to be run on a seeded database instead of by hand (T026). The results file says so. B1 shows the owner may still want one look in the browser; the human stopwatch checks of T025 stay open. |
| M1, M2 | Touch-target sizes were estimated, not measured. Needs a design decision on the control token and a measurement in a browser; not changed blind. |
| M3 | Needs a check of Tomcat's behaviour with a body much larger than 5 MB; to be tested separately. |
| M4, M5, M10 | Behaviour changes to the list (mobile search with +91, filter options, profile heading rule) that are not in the spec; left for the owner to decide. |
| M6 | Follows the F0 precedent for the test-support controllers. |
| M7, M8 | History notes only; nothing to change in the code. |
| M9 | Test-hardening for module imports; not needed for correctness now. |
