Result: FIX FIRST

# Diff review: 002-students (whole branch vs main)

Scope: `git diff main...HEAD` (34 commits, 116 files, T001 to T028), working tree clean. Read: spec.md, tasks.md, plan/contract where needed, constitution, CLAUDE.md, ui-tokens.md. Excluded by request: the owner's T025 stopwatch runs (SC-001, SC-006) and the school-PC export timing.

## Verdict

The backend is in good shape. V6 is a new migration and V1 to V5 are untouched. Every foreign key is `ON DELETE RESTRICT`. The student, enrollment and GR-number repositories have no delete methods. Every write goes through `StudentAudit` in the same transaction, with rollback tested. Aadhaar and religion/caste are masked in the DTO, list, export and audit, and are refused for search, filter and sort. Module imports are guarded by a test. Synthetic data only, and no secrets or forbidden files are tracked. `scripts/verify.sh` passes.

One functional requirement is not delivered in the UI (photo replace and remove on an existing student). The FR coverage gate does not check this feature at all. The manual quickstart task (T026) was ticked after an agent run instead of a run by hand. Fix these before push.

Counts: Blocking 1, Should fix 5, Minor 10.

## Blocking

**B1. FR-005 photo replace and remove are not possible from the UI for a saved student**
- Location: `frontend/src/app/features/students/student-form/student-form.component.html:175-208` (the photo block is only rendered when `editId() === null`); `frontend/src/app/features/students/student-profile/student-profile.component.html` (no photo control); `frontend/src/app/features/students/students.service.ts:169` (`deletePhoto` is never called).
- Problem: FR-005 says the "Admin MUST be able to add, replace and remove a photo". The backend supports this (`PUT`/`DELETE /api/students/{id}/photo`, `StudentPhotoIT.replacingChangesThePhotoVersionAndRemovingShowsNoPhoto`). In the UI, though, a photo can only be chosen while registering. The edit screen and the profile have no way to add a photo later, replace it or remove it. `PhotoUploadComponent.currentUrl` was built for the edit screen but is never bound. Quickstart scenarios 5 and 19 ("replace the photo; remove it") were checked through the API only (`quickstart-results.md:46`), so the gap was not seen.
- Fix: render `app-photo-upload` in edit mode (bind `currentUrl` to `/api/students/{id}/photo?v=…`, call `putPhoto` and `deletePhoto`), and add a Playwright test tagged FR-005 for replace and remove from the screen.

## Should fix

**S1. The strict FR coverage check does not check this feature, and FR ids collide with F0**
- Location: `scripts/check-fr-coverage.sh:9` (`spec=specs/001-foundation/spec.md`); T027 commit `64e44f3` ("strict FR coverage 46 of 46").
- Problem: the gate counts the 46 F0 requirements only. F1's FR-001 to FR-019 reuse the same ids, so any F0 `@Covers("FR-001")` would also "cover" F1's FR-001. Constitution VIII and FR-016 are therefore not enforced for F1. A manual check found that every F1 FR-001 to FR-019 is referenced by at least one test file added or changed on this branch (FR-014 by one file only), so coverage exists today. It is just not checked.
- Fix: make the script read every `specs/*/spec.md` (or the current branch's spec) and use feature-qualified ids in tags (for example `@Covers("F1:FR-005")` or a per-feature prefix) so the two specs cannot satisfy each other.

**S2. T026 is ticked although the required manual run was not done**
- Location: `specs/002-students/tasks.md:113` (`[x] T026 MANUAL (the developer or owner, not an agent)`); `specs/002-students/quickstart-results.md:38` ("Run … by Claude …, not by hand in the browser").
- Problem: the task requires a run by hand on a fresh database. An agent drove the API and the Playwright specs instead. B1 shows what that missed. This is separate from the T025 items excluded by the caller.
- Fix: untick T026 (or mark it PENDING in quickstart-results) until the owner has run scenarios 1 to 19 and section 5 in the browser.

**S3. The History block shows raw database ids for standard, division, category and admission standard**
- Location: `frontend/src/app/features/students/student-profile/history-lines.ts:44-58`; values come from `Student.snapshot()` (`management/src/main/java/com/admin/management/students/Student.java:272-274`) and `StudentEnrollment.snapshot()`.
- Problem: FR-019 asks for "old and new values". A class-record change reads "Standard: 3 → 5" and "Category: 2 → 4", and registration lists `categoryId` and `admissionStandardId` as numbers. The admin cannot read these.
- Fix: resolve the ids to names (server side in `StudentService.history` via `MasterDataLookup` maps, or client side from pick-lists) before display.

**S4. The FR-019 browser test edits through a test-only backdoor, not the real edit API, and the backdoor's comment is stale**
- Location: `management/src/main/java/com/admin/management/students/StudentTestSupportController.java:15-19, 57-65` ("no edit API until F1 US3"); used at `frontend/e2e/students-list-profile.spec.ts:243`.
- Problem: `edit-address` updates the row with raw SQL, bypasses `@Version` and validation, and writes the audit entry by hand. Now that `PUT /api/students/{id}` exists (T021), the History test does not prove that a real edit appears in the History block.
- Fix: use `PUT /api/students/{id}` (or the edit screen) in the test, delete the `edit-address` endpoint and update the class comment.

**S5. `any` in new TypeScript test code (strict TypeScript, no `any`)**
- Location: `frontend/src/app/features/students/class-record-dialog/class-record-dialog.component.spec.ts:42`, `frontend/src/app/features/students/student-form/student-form-edit.spec.ts:44`, `frontend/src/app/features/students/student-form/student-form.component.spec.ts:34`, `frontend/e2e/support/students.ts` (`[key: string]: any` in `createStudent`'s return type), `frontend/e2e/students-edit.spec.ts` (`(category: any)`, `const input: any`).
- Problem: the frontend rule forbids `any`. Specs reach into protected members through `as any`.
- Fix: type the helpers (`Record<string, unknown>`, the `StudentInput` and `Category` types), and test through the DOM or a typed test harness instead of `as any`.

## Minor

**M1.** `frontend/src/app/core/layout/shell.component.html:19-22` and `frontend/src/app/features/setup/setup-layout.component.ts:30-41`: the top-bar tabs and Setup list links use `px-4 py-2`/`px-3 py-2` with `text-[0.95rem]` and no `min-height`. By my estimate they are about 39-40px tall, under the 44px control rule (not measured in a browser). The arbitrary `text-[0.95rem]` is also not a token. Fix: add `min-h-[var(--spacing-control)]` (or the control token) and a token font size.

**M2.** `frontend/src/app/features/students/student-profile/student-profile.component.html:1` ("Back to students") and `frontend/src/app/features/students/student-form/student-form.component.html:30-31` ("Open student", "Add another student") use `.link-button` (`styles.css:176`, padding 0.25rem), well under 44px. Fix: give `.link-button` the control min-height or use `btn btn-sm`.

**M3.** `management/src/main/resources/application.properties:17-18` and `frontend/e2e/students-register.spec.ts:160`: only 5 MB + 1 KB is tested. I am not sure a much larger file (for example 20 MB) gets the 422 "larger than 5 MB" message. Tomcat may reset the connection once the body passes `max-swallow-size` (default 2 MB), and the user would then see a generic error. Fix: check the photo size in `PhotoUploadComponent.onChosen` before sending, and/or test with a 20 MB file.

**M4.** `management/src/main/java/com/admin/management/students/StudentTable.java:96-97`: mobile numbers are stored as normalised 10 digits, so searching with "+91 90000 00001" or with spaces finds nothing. Fix: normalise the search text like `StudentValidator.mobile` before matching mobile columns.

**M5.** `frontend/src/app/features/students/student-list/student-columns.ts:27-52`: the Standard, Division and Category filter options come from pick-lists, which hold active values and current-year divisions only. Students whose shown record has an inactive value or an earlier-year division cannot be filtered to. Fix: feed the filter options from all values (the lookup maps), not from the form pick-lists.

**M6.** `management/src/main/java/com/admin/management/students/StudentTestSupportController.java:35-43`: hard `DELETE FROM students` in an endpoint compiled into the production jar and guarded only by `@Profile("test")`. This follows the F0 precedent and is not a constitution breach as long as the test profile is never active in production. Fix: consider moving test-support controllers to `src/test` or adding a startup check that refuses the `test` profile against the production database.

**M7.** `docs/reports/001-foundation/test-results.md` (added in `d5a637a`, "Specs, plan, tasks for feature 002-students"): an F0 report with `Result: FAIL` is committed on the F1 branch, in a spec commit. Fix: move it to its own commit with a clear message, or drop it if it is superseded.

**M8.** `management/src/test/java/com/admin/management/masterdata/StudentCategoryCodeIT.java:41-44, 59`: an F0 test fix (GET by id → list endpoint) sits in a commit labelled T025 (`9206f3e`). The change itself is sound. Scope note only.

**M9.** `management/src/test/java/com/admin/management/shared/ModuleDependencyTest.java:36`: only `import` lines are checked, so a fully qualified reference to another module's internals in code would pass. Fix: also scan for `com.admin.management.<module>.` tokens outside the allowed public packages.

**M10.** `frontend/src/app/features/students/student-profile/student-profile.component.ts:38` with `management/src/main/java/com/admin/management/students/StudentService.java:221-224`: the profile heading uses `classHistory[0]` (latest `year_start_date`), not "current year else most recent" as the list does. The two are the same today, but they will differ once F4 creates next-year records. Fix: pick the current-year record first, as `StudentTable` does.

**M11.** `management/src/main/java/com/admin/management/students/StudentPhotoController.java:33-39`: the `v` parameter is ignored, and every response, including one without `v`, is cached `immutable` for a year. A request without `v` would keep a stale photo. Fix: send `no-cache` when `v` is absent or does not match `photo_version`.

**M12.** `management/src/test/java/com/admin/management/students/StudentRegistrationIT.java:342-368`: `breakAuditWrites()` and `restoreAuditWrites()` are not in a `finally` or `@AfterEach` (StudentEditIT has a `tearDown`). If an assertion fails in between, the trigger stays and later tests fail for an unrelated reason. Fix: drop the trigger in `@AfterEach`.

## Verify result

`bash scripts/verify.sh` was run for this review on 26-10-2026 at 20:08 (HEAD `0eb66b6`, clean tree): **VERIFY OK**.
- Backend `mvnw verify`: 306 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (the performance-tagged `StudentListPerformanceIT` is excluded by default).
- Frontend: build OK; unit tests 20 files, 147 passed.
- Playwright: 107 passed.
- FR coverage (strict): 46 of 46. This is the F0 spec only (see S1).
- The full log contains no synthetic Aadhaar (`123456789012`, `987654321098`) or religion/caste test values.

Constitution and never-do list: no money code in the diff; no change to an existing migration (only `V6__students.sql` added); no hard delete or cascade on people in product code (only the test-profile endpoint, M6); audit in the same transaction for every student, class record, status and photo change; synthetic data only; `git ls-files` shows no `.env`, `credentials-first-run.txt`, `backups/` or `real-data/`; no sign of force-push in the branch history.
