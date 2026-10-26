# F1 Students: quickstart and timing results

Filled in by the developer or owner. Rows marked **PENDING** are manual runs that an agent cannot do (T025 timed runs, T026).

## Automated timing (T025, `StudentListPerformanceIT`)

Run: `./mvnw verify -Dtest.groups=performance -Dtest.excludedGroups=none -Dtest=StudentListPerformanceIT`, 26-10-2026.

- Machine: dev PC, 20 processors, 8112 MB max heap, Windows 11 (10.0), Java 21.0.11, PostgreSQL 15.6.
- Data: 10,000 synthetic students, 2 to 3 class records each, 12 standards x 4 divisions x 4 years.

| Operation | Time | Limit |
|---|---|---|
| Page 3 of 50, default order | 31 ms | 2000 ms |
| Search by name | 81 ms | 2000 ms |
| Search by GR No. | 93 ms | 2000 ms |
| Search by mobile | 61 ms | 2000 ms |
| Filter by Standard | 133 ms | 2000 ms |
| Filter by Category | 97 ms | 2000 ms |
| Sort by Std / Div | 144 ms | 2000 ms |
| Sort by Category (descending, deep page) | 359 ms | 2000 ms |
| Sort by name (descending) | 25 ms | 2000 ms |
| Status All | 17 ms | 2000 ms |
| Export of 10,000 students | 9671 ms (1.1 MB) | 30000 ms |

SC-003: **pass** on the dev machine. **PENDING:** repeat on the school computer (Windows 10, 4 GB), where the export is the figure to watch.

## Timed manual runs (T025)

| Check | Result |
|---|---|
| SC-001: one student registered in under 2 minutes | PENDING |
| SC-001: 20 students entered without the mouse | PENDING (the automated keyboard-only run of 20 students passes: `students-register.spec.ts`) |
| SC-006: find a student and open the profile in under 30 seconds | PENDING |

## Quickstart scenarios 1 to 19 and section 5 (T026)

Run 26-10-2026 by Claude on a fresh test database (`admin_management_test`, test profile) with synthetic students, not by hand in the browser: scenarios through the API with a driver script, UI and layout scenarios through the Playwright specs (38 of 38 passed: `students-shell`, `students-register`, `students-list-profile`, `students-edit`). The seed data was removed afterwards (test schema dropped; dev database untouched). No defect found.

| Scenario | Result | Notes |
|---|---|---|
| 1 Register | pass | 201, Active, Manual, serial no., one class record |
| 2 Duplicate GR No. | pass | 409 for other capitals and spaces |
| 3 Same name | pass | warning lists the first student, save still 201 |
| 4 Field rules | pass | 422 for empty or 9-digit mobile, future admission date, admission before birth |
| 5 Photo | pass | PNG stored; text file and over 5 MB refused (422); replace changes version; delete gives 404; immutable cache header |
| 6 Keyboard-only 20 students | pass | Playwright `students-register.spec.ts` (8.2 s); API saved 20 more |
| 7 List, search, export | pass | name and GR No. search, paging, export 200 |
| 8 Sensitive values | pass | masked in profile and list; Aadhaar search finds 0; filter 422; full value only from `/edit` with no-store |
| 9 Profile | pass | no fee, receipt or roll number fields |
| 10 Edit, class record | pass | old GR No. stays reserved (409); one row per year |
| 11 Stale saves | pass | second save, stale class record and stale Mark as left all 409 |
| 12 Mark as left | pass | hidden from Active, shown under Left and All; second attempt 422 |
| 13 Left student again | pass | old GR No. refused, new GR No. makes a second record |
| 14 Audit log | pass | Students entries and Export present, no full Aadhaar or religion/caste |
| 15, 16 Shell, no current year | pass | `students-shell.spec.ts` |
| 17 10,000 students | pass | see timing table above |
| 18 Earlier-year record | pass (automated only) | covered by `StudentListIT`; not repeated in the driver |
| 19 History block | pass | entries newest first, masked; Playwright history test |
| Section 5 | pass | `students-shell.spec.ts` |

Not done by an agent: the human stopwatch checks in the table above (SC-001, SC-006) and the repeat on the school computer.
