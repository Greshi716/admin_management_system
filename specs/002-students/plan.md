# Implementation Plan: Students and Class Records (F1)

**Branch**: `002-students` | **Date**: 2026-10-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-students/spec.md`

## Summary

F1 adds the `students` module: register and edit students, a yearly class record per student, a photo,
a server-paged Students list with Export to Excel, and a profile page. It also replaces the F0 sidebar
with the prototype's top bar. It reuses everything F0 decided (see
[../001-foundation/plan.md](../001-foundation/plan.md) and its research R4 to R15): server session, CSRF,
`ApiExceptionHandler`, `@Version` and 409 `STALE_VERSION`, `AuditService` in the caller's transaction, and the
`ColumnDef` / `TableDef` table framework. Only what is new is described here.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.2; TypeScript strict, Angular 22.2 (unchanged from F0)

**Primary Dependencies**: no new library. Photo resizing uses the JDK's `javax.imageio` (see research R2). Excel uses the existing POI exporter.

**Storage**: PostgreSQL 15. New migration `V6__students.sql` (V5 is the last on `main`; V1 to V5 are not edited). Photos are stored in the database.

**Testing**: JUnit 5 `@SpringBootTest` with `@Covers("FR-xxx")`; Angular unit tests; Playwright with FR ids in titles; a module-dependency test (research R7).

**Target Platform**: Windows 10, 4 GB RAM, one PC. List queries select no photo bytes; uploads are limited to 5 MB and to a maximum pixel count (research R2).

**Project Type**: web application (existing `management/` and `frontend/`)

**Performance Goals**: list page, search, filter, sort under 2 s on 10,000 students; export of 10,000 rows under 30 s (SC-003, same size as F0). Same method as F0 `TablePerformanceIT`.

**Constraints**: constitution I to VIII; offline; one admin; no new audit or table framework.

**Scale/Scope**: one school, a few thousand students over the years; 4 stories, 19 FRs, 3 screens plus the shell.

## Constitution Check

*GATE: passes before research; re-checked after design: still passes.*

| Principle | Result |
|---|---|
| I. Exact Money Arithmetic | N/A. No money fields. |
| II. Immutable Finalized Records | N/A (no receipts or payroll). Past-year class records stay editable until F3 adds fee records; F3 must decide the lock (spec review S4). |
| III. Audited Financial Writes | Not financial, but the same rule is applied: `AuditService.record` (`MANDATORY`) in the same transaction for every student and class-record create, edit and status change and every photo add, replace or removal (FR-012). The History block (FR-019) only reads these entries through a new read method on `AuditService`. |
| IV. No Hard Deletes of People | No student, class record or GR No. is ever deleted: no delete endpoint or repository delete exists for them, and all foreign keys are `ON DELETE RESTRICT`. Departure is the status `Left`. Only the photo bytes can be removed (`DELETE /students/{id}/photo`, a `student_photos` row, audited). |
| V. Flyway-Only Schema Changes | One new migration `V6` (the student tables, the sequence and one index on `audit_log (entity, at)` for the History block); `ddl-auto=validate`. |
| VI. No Real Data in the Repo | Fixtures are synthetic. Aadhaar and religion/caste are masked in lists, exports and the audit log; never logged (research R4). |
| VII. Modular Monolith | New package `students`. It uses `shared`, `audit` (`AuditService`) and a new public package `masterdata.api` only. Dependency order: `shared` <- `audit` <- `masterdata` <- `students`; no cycle. A test guards it (research R7). |
| VIII. Every Requirement Is Tested | `@Covers` and Playwright titles for FR-001 to FR-019; the F0 coverage script applies. |

No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-students/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/openapi.yaml
```

### Source Code (changes only)

```text
management/src/main/java/com/admin/management/
├── masterdata/api/                  # NEW public package: MasterDataLookup (+ small records). Implemented in masterdata.
├── students/                        # NEW module
│   ├── Student, StudentEnrollment, StudentPhoto            # entities (ids of master data are plain Long)
│   ├── StudentRepository, EnrollmentRepository, PhotoRepository
│   ├── StudentService, PhotoService (resize), StudentAudit
│   └── StudentController, StudentPhotoController, dto records
├── shared/table/                    # CHANGE: refuse filter and sort on sensitive columns (research R4)
├── shared/masking/SensitiveMasker   # CHANGE: add "religionCaste" to MASKED
└── audit/                           # CHANGE: AuditService.history(area, entities) read method + test (research R12); area is free text, no other change
management/src/main/resources/db/migration/V6__students.sql
management/src/main/resources/application.properties   # multipart limit 5 MB

frontend/src/app/
├── core/layout/                     # CHANGE: shell = top bar + tabs + Setup left list
├── features/students/               # NEW: student-list, student-form (register/edit), student-profile, students.routes.ts
├── styles.css                       # CHANGE: shell colours use existing tokens only (research R8); no new colour values
├── features/masterdata, audit       # routes move under /setup; audit area filter gains "Students"
└── shared/ui/photo-upload.component.ts, avatar.component.ts   # NEW
frontend/e2e/                        # NEW students specs; shell/look-and-feel specs updated (selectors only)
```

**Structure Decision**: same two-folder layout as F0. Students reach master data only through
`masterdata.api.MasterDataLookup`; the tables are linked by database foreign keys only.

## Decisions that differ from the request or that need a flag

1. **The `sensitive` flag on `ColumnDef` already exists** (`asSensitive()`): list and export are masked today. What is missing, and what F1 adds: a filter or sort on a sensitive column is currently accepted and can be used to probe values, so `TableQueryService` must refuse it (422); and `religionCaste` must be added to the audit mask list (R4).
2. **No module cycle exists today** (import scan of `main`): `audit -> shared`, `masterdata -> audit, shared`, `security -> audit, shared`. `shared` does not import `audit` (the `ExportRecorder` interface prevents it). F1 keeps it so and adds a test.
3. **Audit area `Students`** is decided and recorded (decision log 25-10-2026; agreement area list updated). `audit_log.area` is free text, so no schema change for it. The audit-log screen's area filter gains "Students".
4. **List columns Std / Div and Category** show the current year's class record, or the most recent one with its year when there is none (FR-008). They come from correlated subqueries; names come from `MasterDataLookup` (research R5). The ordering of years uses `student_enrollments.year_start_date` (data-model).
5. **Aadhaar and religion/caste are stored as plain text** in PostgreSQL, with no field-level encryption (owner decision, decision log 25-10-2026). They are masked wherever shown except the edit form. The whole database is one backup file.
6. **Mark as left** is a button on the profile for an Active student only, with a confirmation, calling `PUT /students/{id}/left` (FR-004, FR-009). There is no prepare-LC action.
7. **Stale saves** (FR-018): `@Version` on `students` and `student_enrollments`; student edit, class-record edit and Mark as left return the F0 409 `STALE_VERSION` with the latest record.
8. **History block on the profile** (FR-019): `GET /students/{id}/history` reads the audit entries for `Student#id` and for the student's `StudentEnrollment#id` values through a new read method on the public `AuditService` (research R12). No new table; `students -> audit` is already an allowed dependency.
9. **Mobile numbers are shown in full** on the profile and list (owner decision); the student-level category is stored at registration only; the screens show the yearly category.

10. **Security of the edit endpoint**: `GET /students/{id}/edit` returns the full Aadhaar and religion/caste, so it sends `Cache-Control: no-store`; a test checks the header (plan review S7).
11. **Duplicate GR No.** follows the F0 pattern for a duplicate: `ConflictException` (409) carrying the field name `grNo`; other field problems are 422 (plan review M5).
12. **History block** is capped at 200 entries, newest first, with the note "Older entries are in the Audit log" when more exist (owner agreed, 25-10-2026).

## First tasks (checks before building on them)

1. **List query spike (plan review S4)**: with 10,000 synthetic students and 2 or 3 class records each, run page, count, filter, sort and export through the F0 table framework using the "current else most recent record" columns and the `CASE` sort. Must meet SC-003 on the dev machine. If it does not, the fallback is a read-only view or table owned by `masterdata` that exposes the names, or showing the current-year record only in SQL; decide then and record it in research R5.
2. **Small technical checks**: Hibernate `bytea` mapping passes `ddl-auto=validate`; the multipart property names on Spring Boot 4.1.2; a phone photo with a rotation tag comes out upright (research R2); the Excel exporter neutralises cells that start with `=`, `+`, `-` or `@` (add it if not); photo size with sample phone photos.
3. **Layout checks (plan review S6)**: Playwright assertions for the shell and the two screens are listed in the quickstart section 4.

## Complexity Tracking

None.
