# Implementation Plan: Fee Structure and Student Fee Accounts (F2)

**Branch**: `003-fees` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-fees/spec.md`

## Summary

F2 adds the `fees` module: a fee structure per academic year (rows of standard, category, amount, registration fee,
due date) that is Draft until finalised and then changed only by a per-row Revise, a fee account for every class
record of a finalised year, a brought-forward amount per student, and the fee lock on standard and category. It
adds a Fees block to the student profile and a Total fee column to the Students list. It reuses what F0 and F1
decided (see [../001-foundation/plan.md](../001-foundation/plan.md) and F1's plan on branch `002-students`): server
session, CSRF, `ApiExceptionHandler`, `@Version` and 409 `STALE_VERSION`, `AuditService` in the caller's
transaction, the `ColumnDef` / `TableDef` table framework and `MasterDataLookup`. Only what is new is described here.

**Prerequisite**: F1 (`002-students`, migration `V6`, package `students`, the F1 shell) is merged into `main` and
into this branch (merge `124d622`). Every path below assumes F1 is present.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2; TypeScript strict, Angular 22.2 (unchanged)

**Primary Dependencies**: no new library. Money is `BigDecimal` with `NUMERIC(12,2)`; Excel uses the existing POI exporter.

**Storage**: PostgreSQL 15. New migration `V7__fees.sql` (V6 is F1's; V1 to V6 are not edited). No change to the audit table.

**Testing**: JUnit 5 `@SpringBootTest` with `@Covers("FR-xxx")`; pure unit tests for the fee selection rule against the spec's examples; Angular unit tests; Playwright with FR ids in titles; the module-dependency test is extended.

**Target Platform**: Windows 10, 4 GB RAM, one PC.

**Project Type**: web application (existing `management/` and `frontend/`)

**Performance Goals**: finalise, and every confirmed revise, of a year with 2,000 class records in under 10 s (SC-004); the Students list stays under 2 s on 10,000 students with the new Total fee column (F1's SC-003).

**Constraints**: constitution I to VIII; offline; one admin; no new audit or table framework; no float or double for money anywhere, including tests.

**Scale/Scope**: one school, 12 standards, about 7 categories, a few thousand class records per year; 5 stories, 14 FRs, 1 new screen plus changes to 3 F1 screens.

## Constitution Check

*GATE: passes before research; re-checked after design: passes. The one exception (Complexity Tracking) was approved by the project owner on 2026-10-26 and written into Principle VII (constitution 1.1.0, decision log).*

| Principle | Result |
|---|---|
| I. Exact Money Arithmetic | `BigDecimal` and `NUMERIC(12,2)` everywhere; CHECK `>= 0`; more than two decimals is refused, never rounded (FR-011, research R3). The only arithmetic is addition (total fee, total due), which is exact. Tests use `BigDecimal` and compare with `compareTo`. |
| II. Immutable Finalized Records | The principle names receipts and payroll months, and F2 has neither. Revise is the agreement's own controlled exception for fee structures (agreement 5.1): it changes the structure through a confirmed, audited action and recalculates the derived fee accounts; it never touches a receipt (none exist yet; F3 stores receipt amounts itself and never recomputes them, spec FR-004). |
| III. Audited Financial Writes | Every row create, edit, delete, finalise, revise and brought-forward change calls `AuditService.record` (`MANDATORY`) in the same transaction (FR-012, research R5). Fee accounts are derived from the rows, so there is no entry per student: the revise entry holds the row's old and new values, the number of accounts recalculated and the number that actually changed, which is enough to rebuild any account's before and after. |
| IV. No Hard Deletes of People | Nothing about a student or class record is deleted. Fee rows may be deleted (Draft, or Revise) because they are setup data, as master data is; `fee_accounts` and `fee_brought_forward` have no delete path. All foreign keys are `ON DELETE RESTRICT`. |
| V. Flyway-Only Schema Changes | One new migration `V7`; `ddl-auto=validate`. |
| VI. No Real Data in the Repo | Synthetic fixtures only. Fee amounts are not sensitive fields and appear in full in the audit log; the brought-forward note text is never written to it (spec FR-012); sensitive student fields keep the F0 mask. |
| VII. Modular Monolith | New package `fees`. Dependencies: `fees -> students.api, masterdata.api, audit, shared`; `students -> shared` (a new small interface, research R2). Order `shared <- audit <- masterdata <- students <- fees`, acyclic. One read-only list-column exception, allowed by Principle VII (1.1.0), see Complexity Tracking. |
| VIII. Every Requirement Is Tested | `@Covers` and Playwright titles for FR-001 to FR-014; the fee selection rule has unit tests for every example in the spec. |

## Project Structure

### Documentation (this feature)

```text
specs/003-fees/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/openapi.yaml
```

### Source Code (changes only)

```text
management/src/main/java/com/admin/management/
├── shared/ClassRecordFees.java        # NEW interface: isLocked(yearId), classRecordAdded(...). Implemented by fees.
├── shared/web/CodedConflictException  # NEW: code, field, message, optional `current`; handled in ApiExceptionHandler (409 with code and current)
├── shared/web/ApiExceptionHandler     # CHANGE: handle CodedConflictException; add the two fee unique-index names to KNOWN (field standardId / categoryId)
├── shared/excel/ExcelExporter         # CHANGE: BigDecimal written as a numeric cell with format 0.00 (was text); other Number types not truncated; tests updated
├── shared/ModuleDependencyTest        # (src/test) CHANGE: PUBLIC gains "students.api."; ALLOWED gains fees -> students, masterdata, audit, shared; students -> shared only
├── fees/FeeTestSupportController      # NEW, test profile only (behind TestProfileGuard): seed class records in bulk, call row API without confirm
├── students/api/                      # NEW public package: EnrollmentLookup (class records of a year, the shown record), StudentLookup.exists
├── students/                          # CHANGE: StudentService.register calls ClassRecordFees; changeEnrollment refuses
│                                      #   a standard or category change when the year is locked; EnrollmentDto gains feeLocked;
│                                      #   StudentTable gains Total fee columns (read-only subquery, see Complexity Tracking)
├── fees/                              # NEW module
│   ├── FeeRow, FeeYearStatus, FeeAccount, BroughtForward            # entities (ids of other modules are plain Long)
│   ├── FeeRowRepository, FeeYearStatusRepository, FeeAccountRepository, BroughtForwardRepository
│   ├── FeeSelection                   # pure rule: which row and which registration fee apply (FR-005)
│   ├── FeeStructureService            # rows, finalise, revise, recalculation (FR-001 to FR-004, FR-006)
│   ├── BroughtForwardService, StudentFeesService   # FR-007, FR-009
│   ├── FeesAudit                      # area Fees
│   ├── FeeClassRecordHooks            # implements shared.ClassRecordFees
│   └── FeeStructureController, StudentFeesController, dto records
└── audit/                             # no change (area is free text; actions Create, Edit, Delete, Finalise exist; revise is a reason)
management/src/main/resources/db/migration/V7__fees.sql

frontend/src/app/
├── features/fees/                     # NEW: fee-structure (screen), fees.routes.ts, fees.service.ts, revise-dialog.
│                                      #   The year picker uses the F0 academic-years list; the standard and category choices come from
│                                      #   GET /fee-structures/{yearId}/choices (contract); status tag Draft / Finalised and the revise
│                                      #   warning use the existing token pairs (warning bg #FFF3DC, text #6B4200; accent soft for the tag)
├── features/setup/setup-layout.component.ts   # CHANGE: group "Fees and salary" with "Fee structure"
├── features/students/student-profile/         # CHANGE: Fees block
├── features/students/student-list/student-columns.ts   # CHANGE: Total fee column
├── features/students/student-form/            # CHANGE: edit mode gains the Brought forward section
├── features/students/class-record-dialog/     # CHANGE: fixed standard and category with the lock message
└── features/audit/                            # CHANGE: area filter gains "Fees", action filter gains "Finalise" if missing
frontend/e2e/                                  # NEW fees specs; students specs updated
```

**Structure Decision**: same two-folder layout as F0 and F1. `fees` reads class records only through
`students.api`; `students` reaches fees only through the `shared` interface; tables are linked by foreign keys.

## Decisions that differ from the request or that need a flag

1. **F1 is on this branch.** F2 changes F1 code (class record change, registration, list, profile, form). The migration number `V7` and the `students.api` package assume F1 as merged from `002-students`.
2. **Revise confirmation is enforced by the server.** A row change in a Finalised year without `confirm=true` returns 409 `REVISE_CONFIRMATION_REQUIRED` with the student count in `current`; the screen shows that count in its warning and repeats the call with `confirm=true` (research R4). The count is the number of class records of the year, as the spec says. F1's `ConflictException` has no code and no `current`, so a new `CodedConflictException` (code, field, message, current) and its handler are added (research R4, R8); the same type carries `FEE_STRUCTURE_FINALISED`.
3. **Per-row revise recalculates the whole year** (spec clarification). The audit entry for a row change in a Finalised year uses the action Create, Edit or Delete with the reason `Revise`, `studentsRecalculated` and `accountsChanged` in the changes. The audit table's action list is not changed (F0 FR-027); finalising uses the existing action Finalise. A revise is found by area Fees and the reason (owner question Q2 of the plan review: kept as is until the owner asks for a separate action).
4. **Fee not set / not finalised are states, not amounts.** A `fee_accounts` row exists only for class records of a finalised year; its `amount` is null when no row applies (research R6).
5. **Brought-forward is its own record** (`fee_brought_forward`, one per student, own `version`, own endpoint) so it can be saved without saving the student, has its own audit entry, and F3 can read it without touching student data. The Edit details screen shows a Brought forward section with its own save button, with a hint that the note is a short reason (at most 200 characters; the audit log records that it changed, not its text).
6. **List column.** Total fee is the yearly fee plus the registration fee of the shown record, read with a correlated subquery on `fee_accounts` (Complexity Tracking). An internal column carries the state text so the screen can show "Fee not set" or "Not finalised".
7. **`affects_fees` is not used.** It stays a hint on categories (decision log); the fee screen offers every active category.
8. **Guard against concurrent finalise and registration**: finalise and every revise take a row lock on the year's status row (`PESSIMISTIC_WRITE`); adding a class record to a year takes a shared lock on it (`PESSIMISTIC_READ`) (research R7).
9. **Structure version on every row request.** Every row change bumps the year's `fee_year_status.version`, and every row request carries the `structureVersion` the screen loaded; a mismatch returns the F0 409 `STALE_VERSION` with the latest structure, before any revise warning (research R4, spec FR-013). This is how a window that still shows Draft learns the year was finalised elsewhere.
10. **Amounts in the Excel export are numeric cells.** The F0 exporter writes `BigDecimal` as text; it is changed to write a numeric cell with format `0.00` (research R9). This changes a shared class, so its tests and the F0 and F1 export tests are updated in the same task. Owner approval of this shared change is assumed (plan review Q3) and noted in the decision log.
11. **Table shapes differ from the agreement's data section** (no `carried_forward` flag, `registration_fee` on the account, new `fee_year_status` and `fee_brought_forward`). Recorded in the decision log (2026-10-26) and in the agreement's section 11; sections 4.1 and 4.4 get a note pointing to it.

## First tasks (checks before building on them)

1. **Start from a green build**: run `scripts/verify` once on the merged branch before any change.
2. **Small checks**: confirm `SensitiveMasker.MASKED` holds no field name this feature uses (`amount`, `registrationFee`, `note`); confirm `ApiProblem.current` serialises `{ "studentCount": n }`; confirm the Hibernate lock modes in decision 8 produce `FOR UPDATE` and `FOR SHARE` on PostgreSQL 15 and work together with `@Version`.
3. **Performance spike**: finalise and revise a year with 2,000 synthetic class records (R7), and rerun F1's list spike (10,000 students) with the `fee_accounts` subquery; both must meet the goals above before the screens are built. A second test registers a student while a finalise is running and checks the student ends with an account.
4. **Layout checks**: Playwright assertions for the fee structure screen, the Fees block and the class record dialog are listed in the quickstart section 4.
5. **Frontend money rule**: amounts are formatted to two decimals with Indian digit grouping and never added in the browser; totals come from the server (research R3).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| The Students list (module `students`) reads table `fee_accounts` (module `fees`) with a read-only subquery for the Total fee column. Constitution VII forbids direct access to another module's tables. | The list is server-paged with sort and filter on every column (F0 FR-029 to FR-033), so Total fee must be computed in SQL with the page. Same pattern as the accepted exception for `AuditEntry.userName` (decision log, open items). F3 will need the same for paid and pending. Approved by the owner on 2026-10-26; Principle VII now allows it (constitution 1.1.0). `ModuleDependencyTest` reads import lines only and cannot see SQL, so it gets a second test that lists the allowed cross-module table reads (`audit_log` joins `users`; `students` reads `fee_accounts`) by scanning the sources for table names, so the exception cannot grow unnoticed. | (a) Putting the fee columns on `student_enrollments` and having `fees` write them through a `students.api` writer keeps the rule but splits one concept (the fee account) over two modules and makes F3's balances awkward. (b) Loading the page first and adding fees afterwards breaks sorting and filtering on Total fee and the Excel export. (c) A database view owned by `fees` still lets `students` read another module's data. |
