# Implementation Plan: Foundation (F0)

**Branch**: `001-foundation` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-foundation/spec.md`

## Summary

F0 delivers the base every later module sits on: single-admin sign-in with a server session,
master data (academic years, standards, divisions, student categories), an append-only audit
log, an app shell, and one reusable server-paged table with search, column filters, sorting and
Excel export. The backend is the existing Spring Boot module (`management/`) with a REST JSON
API under `/api`; the frontend is a new Angular app (`frontend/`) using plain Angular components styled with Tailwind (PrimeNG was dropped, see research R2) and a custom
light-purple theme. In production the Angular build is served by the same Spring Boot app.

## Technical Context

**Language/Version**: Java 21 (Spring Boot 4.1.2); TypeScript strict (Angular 22.2)

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation, Flyway (+ `flyway-database-postgresql`), Apache POI `poi-ooxml` 5.5.1; Angular 22, Tailwind CSS 4.3, `@fontsource/plus-jakarta-sans`, `@fontsource/noto-sans-gujarati`

**Storage**: PostgreSQL 15 (local dev, `admin_management_db`); separate `admin_management_test` for tests

**Testing**: JUnit 5 + `@SpringBootTest`/MockMvc on PostgreSQL; Angular unit tests; Playwright for sign-in and master-data flows

**Target Platform**: One school computer (Windows), browser access over the school network; Docker packaging comes in a later phase

**Project Type**: web application (Spring Boot backend + Angular frontend served by the backend)

**Performance Goals**: page/search/filter/sort result under 2 s on 10,000 rows (SC-006); 10,000-row export under 30 s (SC-007)

**Constraints**: offline-capable (fonts and assets self-hosted); one admin, no RBAC; constitution Principles I-VIII

**Scale/Scope**: one school, one admin; tens of thousands of rows over the years; 6 user stories, 46 FRs, about 8 screens

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design: still passes.*

| Principle | Gate result |
|---|---|
| I. Exact Money Arithmetic | N/A in F0 (no amounts). Convention (`BigDecimal`, `NUMERIC(12,2)`) carried into the table framework's column types for later modules. |
| II. Immutable Finalized Records | N/A for receipts/payroll. `audit_log` is made append-only by a database trigger (FR-024). |
| III. Audited Financial Writes | `AuditService.record` joins the caller's transaction (`Propagation.MANDATORY`): no audit entry, no commit (FR-023). Auth events use their own transaction so failed sign-ins persist. |
| IV. No Hard Deletes of People | N/A (no people in F0). Master data follows FR-020: delete only when unreferenced; standards, divisions and categories can be marked inactive instead, academic years cannot. |
| V. Flyway-Only Schema Changes | All schema in `V2+` migrations; `V1__baseline.sql` (empty, already applied) is not edited; `ddl-auto=validate`. |
| VI. No Real Data in the Repo | Seed data is the 7 school categories. Fixtures are synthetic. First-run secrets are never logged. **Note**: masked values in the audit log differ from the agreement's "kept out of logs" wording; the agreement and decision log are already updated (T012). |
| VII. Modular Monolith | One package per module (`security`, `masterdata`, `audit`, `shared`); other modules use only public API types. |
| VIII. Every Requirement Is Tested | `@Covers("FR-xxx")` on backend tests, FR IDs in Playwright titles, and a script that fails the build when an FR has no test reference. |

No violations; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
management/
├── pom.xml                                   # + poi-ooxml
├── src/main/java/com/admin/management/
│   ├── ManagementApplication.java
│   ├── shared/
│   │   ├── web/                              # ProblemDetail error model, SPA fallback controller
│   │   ├── table/                            # ColumnDef, TableRequest/TablePage, TableQueryService (public)
│   │   └── excel/                            # streaming xlsx writer (public)
│   ├── security/                             # User, login/logout, lock-out, bootstrap and admin password reset, SecurityConfig
│   ├── masterdata/
│   │   ├── academicyear/  standard/  division/  category/    # entity, repository, service, controller
│   └── audit/                                # AuditService (public), AuditEntry, AuditController, SensitiveMasker
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/                         # V1__baseline.sql (untouched), V2__users_audit.sql, V3__master_data.sql, V4__categories.sql, V5__audit_log_no_truncate.sql (a later index migration would be V6)
└── src/test/java/com/admin/management/       # integration + unit tests, @Covers("FR-xxx")

frontend/
├── angular.json, package.json, proxy.conf.json, playwright.config.ts
├── src/
│   ├── styles.css                            # Tailwind + CSS variables from the preset
│   └── app/
│       ├── core/                             # auth store (signals), interceptors, guards, shell layout
│       ├── shared/                           # table/ (CampusTable), forms, toast, skeleton
│       ├── styles.css                        # the @theme block (only place with colours)
│       └── features/                         # auth/, masterdata/{academic-years,standards,divisions,categories}/, audit/
└── e2e/                                      # Playwright specs (FR IDs in titles)

scripts/                                      # verify.ps1/.sh extended: frontend build/tests, FR coverage check
docs/design/                                  # UI direction (ui-tokens.md); no mockup images
```

**Structure Decision**: Web application with the existing `management/` folder as the backend
(per `CLAUDE.md`) and a new `frontend/` folder. The Angular production build is copied into
`management/src/main/resources/static` (git-ignored) so one Spring Boot process serves both.

## Complexity Tracking

No constitution violations to justify. Mapping note (Principle VII): the academic year lives in `masterdata/academicyear`, and other modules reach the current year only through the `CurrentAcademicYearProvider` interface in `shared`, never through masterdata classes.
