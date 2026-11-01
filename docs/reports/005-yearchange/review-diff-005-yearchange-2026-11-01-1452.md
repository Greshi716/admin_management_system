Result: FIX FIRST

# Diff review: 005-yearchange (whole branch `main...HEAD`)

- Branch: `005-yearchange`, HEAD `8955de6`.
- Scope: the whole branch (`git diff main...HEAD`, 163 files changed, +14,843 / -141).
- Reviewer: diff-reviewer agent, 2026-11-01 14:52.

## Verdict

The implementation covers all 40 functional requirements (FR-001 to FR-040) with extensive unit and integration tests (backend: 100% test pass; frontend unit: 310 tests pass; FR coverage: 40/40 covered). However, three blocking issues must be fixed before committing:
1. `clearStudents()` in `StudentTestSupportController` does not clear new admissions and promotion tables, causing cascading foreign key violations across subsequent browser tests.
2. In `admission-review.component.ts`, when a Google Form category cannot be mapped to an existing student category, `categoryId` remains null without fallback, blocking the approval confirmation dialog in `admissions.spec.ts`.
3. 85 Tailwind palette color classes were used across multiple newly added frontend templates, violating design token rules and failing `theme-conformance.spec.ts` (FR-043).

Counts: 3 blocking, 2 should fix, 2 minor.

## Blocking

**B1. `clearStudents()` in `StudentTestSupportController` fails to delete admissions and promotion records**
- Location: `management/src/main/java/com/admin/management/students/StudentTestSupportController.java:100-108`
- Problem: `clearStudents()` deletes from `student_photos`, `student_enrollments`, `student_gr_numbers`, and `students`. In F4, `admission_requests` (FK to students) and `promotion_run_items` (FK to students and enrollments with append-only triggers) prevent deletion with ON DELETE RESTRICT foreign keys, leaving uncleared students that break subsequent test runs.
- Fix: Set `session_replication_role = replica` in the transaction and delete from `admission_requests`, `admission_import_batches`, `promotion_corrections`, `promotion_run_items`, `promotion_runs` before deleting students and enrollments.

**B2. Admission review approval blocked when form category is unmapped**
- Location: `frontend/src/app/features/admissions/review/admission-review.component.ts:174-184` & `frontend/e2e/admissions.spec.ts:101-109`
- Problem: In `admissions.spec.ts`, the imported row uses category "General", which does not match seeded categories. When reviewed, `categoryId` remains null; since `categoryId` is required, clicking "Approve" fails validation and the confirmation dialog does not open.
- Fix: In `admission-review.component.ts`, fallback to `picks.categories[0].id` when the form category is unmatched, matching the behavior of `divisionId`.

**B3. 85 Tailwind palette class violations failing theme conformance (FR-043)**
- Location:
  - `frontend/src/app/core/layout/shell.component.html:36` (`bg-amber-400`, `text-slate-900`)
  - `frontend/src/app/features/admissions/list/admissions-list.component.html` (`bg-slate-100`, `bg-slate-50`, `bg-amber-100`, `text-amber-800`, `border-amber-300`, `bg-emerald-50`, `text-emerald-800`, etc.)
  - `frontend/src/app/features/admissions/review/admission-review.component.html` (`bg-amber-50`, `text-amber-800`, `text-rose-600`, `bg-rose-50`, `border-rose-300`, etc.)
  - `frontend/src/app/features/admissions/upload-dialog/upload-dialog.component.html` (`border-rose-300`, `bg-rose-50`, `text-emerald-700`, `bg-purple-50`, etc.)
  - `frontend/src/app/features/masterdata/academic-years/academic-years.component.html:158` (`border-gray-300`)
  - `frontend/src/app/features/masterdata/standards/standards.component.html:148` (`border-gray-300`)
- Problem: Templates use Tailwind built-in palette classes (`slate-*`, `amber-*`, `emerald-*`, `rose-*`, `gray-*`) instead of design token classes defined in `styles.css`.
- Fix: Replace palette classes with semantic design tokens (`bg-surface`, `bg-page`, `text-ink`, `text-muted`, `border-border`, `bg-warning-bg`, `text-warning-text`, `bg-error-bg`, `text-danger-text`, `text-success`, badge tokens).

## Should fix

**S1. `fr-coverage-check.spec.ts` fails on Windows when WSL is unconfigured**
- Location: `frontend/e2e/fr-coverage-check.spec.ts:15`
- Problem: `spawnSync('bash', ...)` invokes Windows `System32\bash.exe`, failing with WSL process errors when WSL is not configured.
- Fix: On Windows, use Git Bash (`C:\Program Files\Git\bin\bash.exe`) if present, or detect available bash.

**S2. `audit-log.spec.ts` contains order-dependent assumption in FR-019 test**
- Location: `frontend/e2e/audit-log.spec.ts:88`
- Problem: `expect(page.getByRole('row').filter({ hasText: 'testadmin' })).toHaveCount(0)` assumes no earlier test created a student. When `admissions.spec.ts` runs first, a student is created and audited, causing this check to fail.
- Fix: Verify that filtering by Area `Students` excludes other areas (`Security` and `Master data`), rather than assuming total `Students` audit rows is 0.

## Minor

**M1. Missing aria-labels on icon buttons in admissions review**
- Location: `frontend/src/app/features/admissions/review/admission-review.component.html`
- Problem: Close/back buttons lack explicit `aria-label` attribute.
- Fix: Add `aria-label="Back to admissions list"` on the navigation link.

**M2. Checkbox input styling in academic-years copy option**
- Location: `frontend/src/app/features/masterdata/academic-years/academic-years.component.html:158`
- Problem: Checkbox uses `border-gray-300 rounded text-accent` instead of standard input token styling.
- Fix: Replace `border-gray-300` with `border-input-border rounded-control`.

## Verify result

- Backend tests: PASS (All test suites passed; 0 failures, 0 errors, 0 skipped).
- Frontend unit tests: PASS (42 test files, 310 tests passed).
- Frontend browser tests: FAIL (E2E browser tests failed due to B1, B2, B3, S1, S2).
- FR coverage: PASS (40 of 40 covered on branch `005-yearchange`, 46 of 46 total).
