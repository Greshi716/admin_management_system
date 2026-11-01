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

## Automated Verification & Measurable Outcomes (T029 / `F4QuickstartIT`)

Verification run on 2026-11-01 via `mvnw test -Dtest=F4QuickstartIT` (9 tests passed in ~29.8s).

| Outcome / Scenario | Target Specification | Automated Result | Status |
|---|---|---|---|
| **SC-001** | Create new year copying master data & fees in < 2000 ms | Year setup & copy finished in < 2000 ms (measured < 500 ms) | PASS |
| **SC-002** | Promotion of standard with 60 students in < 3000 ms | Promoted 60 students in < 3000 ms (measured ~120 ms) | PASS |
| **SC-003** | Std 10 promotion split to Std 11 Science & Commerce | 30 students promoted: 15 Science, 15 Commerce with division preserved | PASS |
| **SC-004** | Hand-worked carry-forward fee calculations | Case 1: ₹12,000 fee - ₹9,000 paid = ₹3,000 carried.<br>Case 2: Existing ₹5,000 + ₹2,000 paid + ₹3,000 carried = ₹8,000 total, ₹6,000 pending | PASS |
| **SC-005** | Audit trail completeness for promotion & carry-forward | Audit entries created for each promoted enrollment and fee carry-forward under area `Students` | PASS |
| **SC-006** | Concurrency: concurrent / repeat promote refused with 409 | Second submission rejected with `409 Conflict` (`PROMOTION_VERSION_CONFLICT`) | PASS |
| **SC-007** | 200-row admission file upload in < 3000 ms; re-upload skip | 200 rows parsed & imported in < 3000 ms (measured ~350 ms). Second upload: 200 skipped | PASS |
| **SC-008** | Duplicate admission flagging & existing GR No. conflict | Duplicate candidate flagged (`duplicateFlag = true`); duplicate GR No. rejected with `409 Conflict` | PASS |
| **SC-009** | Quickstart Scenarios 1 to 9 End-to-End Walkthrough | Full workflow: Year copy -> Incomplete promotion warning -> Promotion & carry-forward -> Repeat save refusal -> Admission review, approval, and rejection | PASS |
