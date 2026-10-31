# Implementation Plan: Year Change and Admissions (F4)

**Branch**: `005-yearchange` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-yearchange/spec.md` (with the five clarify answers of 2026-10-31 and the owner rows of the decision log of 2026-10-31)

## Summary

F4 adds two modules to the Spring Boot application and two groups of screens to the Angular app.

1. **`yearchange`** (new package): copy the last year's divisions and fee rows into a new year (due dates + 1 year), a per-standard promote screen, the "promotion not done" warning, and the carry-forward of the unpaid year fee into the student's single brought-forward amount. A promotion is saved per standard in one transaction and recorded in two append-only tables (`promotion_runs`, `promotion_run_items`) that later feed the developer correction.
2. **`admissions`** (new package): upload the Google Form responses file (.xlsx or .csv), a waiting list (`admission_requests`) with duplicate flags, a review form, approve (creates the student and class record through the existing F1 registration) or reject, and the Pending count on a new Approvals tab.

It reuses F0 to F3 unchanged where it can: session and CSRF, `ApiExceptionHandler` and its coded conflicts, `AuditService`, `TableDef` / `TableEndpoints` / Excel exporter, `MasterDataLookup`, `students.api`, `FeeSelection`, `Money`, the `ClassRecordFees` hook and `FeeAccount` creation. What is new is described here; the reasons are in [research.md](research.md).

**Changes to existing code (named so review can find them):** F3 `ReceiptRules` gets one new check (FR-027); the Students list gains two columns (Year status, Last year result) and a "Not promoted yet" tag; the academic year create call gains a copy option; the shell gains the Approvals tab and a warning bar.

**Prerequisite**: F3 (`004-fee-collection`, migration `V8`) is merged into `main` (merge `c1ccb5a`) and this branch contains it.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2, Maven; TypeScript strict, Angular 22 standalone with signals (unchanged)

**Primary Dependencies**: no new library. The `.xlsx` upload is read with the Apache POI already used for the export; `.csv` is read by a small parser written in the project (research R7). Money is `BigDecimal` with `NUMERIC(12,2)`.

**Storage**: PostgreSQL; one new migration `V9__year_change.sql` (tables `promotion_runs`, `promotion_run_items`, `promotion_corrections`, `admission_import_batches`, `admission_requests`, the developer function `correct_promotion`; plus two additive columns: `student_enrollments.stream` and `standards.uses_stream`, because Std 11 and 12 are one row each). No applied migration is edited. `students` and `fee_brought_forward` keep their shape.

**Testing**: JUnit 5 `@SpringBootTest` with `@Covers("FR-xxx")`; unit tests for the due-date shift, the carry-forward arithmetic, the file parser and the duplicate rule; integration tests for a full-standard promotion (including repeat save and two simultaneous saves), the audit rows, the append-only triggers and the correction function; Angular unit tests; Playwright for copy year, promote and the upload-approve-reject flow. The F4 reference cases for carry-forward are written in the test plan from the spec (12,000.00 fee with 9,000.00 paid gives 3,000.00).

**Target Platform**: Windows 10, 4 GB RAM, one PC (checked in F11).

**Project Type**: web application (existing `management/` and `frontend/`).

**Performance Goals**: promoting a standard of 60 students saves in under 5 s; the whole school (about 1,000 students) in under 60 s of server time (spec SC-002 asks for under an hour of admin work); a 200-row upload shows its result in under 30 s (SC-007); the Students list stays under 2 s on 10,000 students with the two new columns (F1 SC-003).

**Constraints**: constitution I to VIII; offline; one admin; no float or double for money anywhere including tests; every promotion and carry-forward audited in the same transaction; no hard delete of students, class records, receipts, fee rows or admission requests; no real data in fixtures (the sample responses file is synthetic).

**Scale/Scope**: about 1,000 students, 12 standards, 2 to 4 divisions each; a responses file of up to 2,000 rows (5 MB limit). About 8 screens or dialogs: copy option on the year form, promote page, promotion warning bar, Approvals list, upload dialog, review form, Students list columns, the registration check message.

## Constitution Check

*GATE: passes before Phase 0; re-checked after Phase 1 (below).*

| Principle | Result | How |
|---|---|---|
| I Exact money | Pass | Carry-forward is `BigDecimal` arithmetic on `NUMERIC(12,2)`; `compareTo` for comparisons; no rounding is needed (amounts are sums and differences of two-decimal values). |
| II Immutable finalised records | Pass | Receipts and fee rows are not touched. The carry-forward changes only `fee_brought_forward`, the F2 mutable amount, whose F3 rule (never below previous-years receipts) is kept. Run and item tables are append-only (triggers). |
| III Audited financial writes | Pass | Each carry-forward writes a Fees audit row (before, after, old year, new year) in the same transaction; if the audit write fails the promotion fails. |
| IV No hard deletes of people | Pass | Nothing is deleted. Left sets a status. All foreign keys are `ON DELETE RESTRICT`. |
| V Flyway only | Pass | `V9__year_change.sql`; Hibernate stays on `validate`. |
| VI No real data | Pass | Synthetic sample files; audit values for sensitive fields keep the masking rule (Aadhaar, religion/caste in `form_data` are masked in the audit row and in lists). |
| VII Modular monolith | Pass, with the entries in Complexity Tracking | `yearchange` and `admissions` call other modules only through `students.api`, `fees.api` and `masterdata.api` (three small interfaces are added). Cross-module graph stays acyclic: `yearchange` and `admissions` depend on `students`, `fees`, `masterdata`, `audit`; none of those depend on them. |
| VIII Every requirement tested | Planned | Each FR-001 to FR-040 is mapped to a test in the test plan; `scripts/check-fr-coverage.sh --strict` is part of verify. |

**Post-design re-check**: no new violation. The one item that needs the owner (the developer correction function) is a named exception of the same kind as `void_receipt`, listed below.

## Project Structure

### Documentation (this feature)

```text
specs/005-yearchange/
├── plan.md              # This file
├── research.md          # Phase 0: decisions and open points
├── data-model.md        # Phase 1: tables, state, rules
├── quickstart.md        # Phase 1: how to see it working end to end
├── contracts/
│   └── openapi.yaml     # Phase 1: new endpoints
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks), not created here
```

### Source Code (repository root)

```text
management/src/main/java/com/admin/management/
├── yearchange/                       # NEW module
│   ├── YearSetupService.java         # copy divisions + fee rows into the new year (FR-001..005)
│   ├── PromotionService.java         # one transaction per standard (FR-006..019)
│   ├── PromotionRules.java           # next standard, stream choice, validation (pure, unit tested)
│   ├── PromotionStatusService.java   # warning data: standards still to do (FR-014, FR-015)
│   ├── PromotionController.java      # /api/year-change/...
│   ├── PromotionRun.java, PromotionRunItem.java + repositories
│   └── dto/ (PromoteScreenDto, PromoteInput, PromoteResultDto, YearChangeStatusDto)
├── admissions/                       # NEW module
│   ├── ResponsesFileReader.java      # .xlsx (POI) and .csv; header mapping; row checks (FR-028..031)
│   ├── DuplicateRule.java            # name match or name+dob+mobile (FR-032, pure)
│   ├── AdmissionImportService.java   # batch, skip already imported, flags
│   ├── AdmissionReviewService.java   # edit, approve, reject (FR-033..035)
│   ├── AdmissionController.java      # /api/admissions/...
│   └── AdmissionRequest.java, ImportBatch.java + repositories
├── fees/api/                         # NEW public interface (FeeCarryForward, FeeStructureCopy)
├── fees/ReceiptRules.java            # CHANGED: registration all at once (FR-027)
├── students/api/                     # CHANGED: ClassRecordPromotion (create next-year record, set year status), StudentRegistration
├── students/StudentTable.java        # CHANGED: Year status and Last year result columns
├── masterdata/api/                   # CHANGED: DivisionCopy
└── masterdata/academicyear/          # CHANGED: create accepts copyFromLast

management/src/main/resources/db/migration/V9__year_change.sql
management/src/test/java/com/admin/management/{yearchange,admissions,fees}/...

frontend/src/app/features/
├── yearchange/                       # promote page, copy option, warning bar
├── admissions/                       # Approvals list, upload dialog, review form
└── students/                         # list columns, "Not promoted yet" tag
frontend/src/app/core/layout/         # Approvals tab with count; warning bar slot

docs/runbooks/correct-promotion.md    # developer correction (new)
```

**Structure Decision**: two new top-level packages, one per constitution module ("academic-year" work in `yearchange`, admissions in its own package so students stays free of file parsing). Writes to students, enrollments and fees go through three narrow `api` interfaces, as F2 and F3 already do with `ClassRecordFees` and `StudentLookup`.

## Phase 0 and Phase 1 outputs

- [research.md](research.md): 12 decisions (R1 to R12) and the points the owner must confirm.
- [data-model.md](data-model.md): new tables, the carry-forward formula, state changes.
- [contracts/openapi.yaml](contracts/openapi.yaml): the new endpoints.
- [quickstart.md](quickstart.md): the end-to-end walk-through.

## Complexity Tracking

| Item | Why needed | Simpler alternative rejected because |
|---|---|---|
| **Developer correction function `correct_promotion(item_id, reason, done_by)`** in `V9`, run by hand through `psql`, never called by the application: the second permitted manual data change under Principle V after `void_receipt`. Needs a decision-log row and owner approval. | FR-025 and the owner answer of 2026-10-31 require a developer-run correction with a reversing entry and an audit entry. | Doing it with ad-hoc SQL has no audit and no guard; a screen was ruled out by the owner. |
| **Students list reads `student_enrollments` twice** (self-join for "Last year result"). Same module's own table, so not a Principle VII exception. | FR-013 needs students who stayed shown apart from promoted ones, sortable and exportable like every list column. | Reading `promotion_run_items` from students would be a cross-module table read and need an exception. |
| **F3 change (FR-027)** in `ReceiptRules`. A change to a finished module, not a violation. | Owner answer 2026-10-31 (clarify Q3): registration paid all at once, otherwise an unpaid part would vanish at year change. | Writing off or carrying the part conflicts with the decision that registration never carries. |
| **Decision-log rows to add** (owner to confirm): the F3 registration check; Std 12 and left students (past-students record in F5); carry-forward adds to the existing amount (R3); the correction function. | The agreement must not be contradicted without a recorded decision. | n/a |
