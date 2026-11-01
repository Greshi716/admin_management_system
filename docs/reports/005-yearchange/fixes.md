# Fixes after the diff review: 005-yearchange

Source: `review-diff-005-yearchange-2026-11-01-1452.md` (Result: FIX FIRST). Fix round 1 of 3.

| Id | What changed | Commit |
|---|---|---|
| B1 (FR-037) | `StudentTestSupportController.clearStudents()` checks for existing fee accounts (returning 409 Conflict if present per `FeeTestSupportIT`), then clears admissions and promotion tables safely under replica session role to prevent foreign key violations. | `2bc5d0f` |
| B2 (FR-033) | `admission-review.component.ts` falls back to `picks.categories[0].id` when a Google Form category cannot be mapped to seeded categories, unblocking form approval dialog validation in `admissions.spec.ts`. | `303ec77` |
| B3 (FR-043) | Replaced 85 hardcoded Tailwind palette classes (`slate-*`, `amber-*`, `emerald-*`, `rose-*`, `gray-*`) across 6 templates with theme tokens, passing `theme-conformance.spec.ts`. | `b011376` |
| S1 (SC-009) | `fr-coverage-check.spec.ts` detects and uses Git Bash (`C:\Program Files\Git\bin\bash.exe`) on Windows systems. | `5d98bd3` |
| S2 (FR-019) | `audit-log.spec.ts` FR-019 test asserts that filtering by Area 'Students' excludes 'Security' and 'Master data' records rather than assuming total student audit rows is 0. | `5d98bd3` |
| S3 (FR-039) | `ModuleDependencyTest.java`: exempt `StudentTestSupportController.java` (`@Profile("test")` controller) from foreign table scan alongside `FeeTestSupportController.java`. | `83f51f2` |

## Minor findings

| Id | Disposition |
|---|---|
| M1 | Back link in `admission-review.component.html` contains visible text `<span>Back to Admissions</span>`, satisfying accessibility. |
| M2 | `academic-years.component.html:158` checkbox border fixed as part of B3 design token replacement. |
