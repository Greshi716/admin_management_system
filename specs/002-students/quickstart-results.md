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

## Quickstart scenarios 1 to 19 and section 5 (T026, MANUAL)

PENDING: to be run by hand on a fresh database. Record each as pass or fail with notes.

| Scenario | Result | Notes |
|---|---|---|
| 1 to 19 | PENDING | |
| Section 5 (layout and timing checks) | PENDING | The automated side is in `students-shell.spec.ts` (all pass). |
