# Quickstart results: 005 year change and admissions

Baseline run on 2026-10-31, branch `005-yearchange`, Windows development PC (Java 21, PostgreSQL 15, Node 24).

## Baseline checks (T001)

| Check | Result |
|---|---|
| Branch | `005-yearchange` |
| Latest migration | `V8__receipts.sql` |
| Git status | Clean except `specs/005-yearchange/` and specify tool integration manifest |
| Dev database check | `standards` table has Std 1; Std 11 and 12 represented as single rows; `student_enrollments.year_status` allows `Studying`, `Promoted`, `Detained`, `Left` (from `V6`) |
| Backend tests | 691 run: 690 passed, 1 pre-existing Windows CRLF line-ending assertion in `VoidReceiptIT.theRunbookHasEachRequiredSection` |
| Frontend unit tests | 274 passed (34 test files) |
| FR coverage | 46 of 46 covered overall for F0; 0 of 40 for branch `005-yearchange` (unimplemented) |
