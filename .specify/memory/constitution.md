# CampusFlow Constitution

## Core Principles

### I. Exact Money Arithmetic
All monetary amounts MUST be `BigDecimal` in Java and `NUMERIC(12,2)` in the database.
`float`, `double`, and their wrapper types MUST NOT hold or compute money anywhere, including
tests and exports. Rounding MUST follow the rules in the Product Agreement (section 5).
Rationale: fees and salaries must be exact to the paisa and match the school's Excel totals.

### II. Immutable Finalized Records
A finalized receipt or payroll month MUST NOT be updated or deleted. Corrections MUST be made
by a reversal entry (e.g. receipt cancellation with a reason) followed by a new entry where
needed. Later rule changes apply only from their effective month; finalized values are stored
and never recalculated. Rationale: the ledger must be trustworthy and reproducible.

### III. Audited Financial Writes
Every financial write (receipts, cancellations, payroll, fund ledger, expenses, fee changes)
MUST write an audit log entry recording who, what, when, and the before/after values, in the
same transaction as the write. A financial write without an audit entry MUST fail.

### IV. No Hard Deletes of People
Students and staff (including teachers) MUST NOT be hard-deleted. Departure is modelled by
status (Active/Left) plus history and register records (LC, staff exit). Foreign keys to
people MUST NOT use cascade delete. Rationale: history, receipts, and payslips must stay
resolvable.

### V. Flyway-Only Schema Changes
All schema changes MUST be Flyway migrations committed to the repository. Applied migrations
MUST NOT be edited; fixes are new migrations. Hibernate/JPA schema auto-generation MUST be
disabled (`validate` or `none`), and manual changes to a database are prohibited.

### VI. No Real Data in the Repo
Real student, parent, staff, bank, Aadhaar, or salary data MUST NOT appear in the repository,
prompts to AI tools, logs, or test fixtures. Tests and examples MUST use synthetic data.
Sensitive fields MUST be masked in lists and kept out of application logs. The audit log
stores sensitive fields only as masked values (last 4 characters visible) and never a
password, hash or recovery code. Rationale: the data belongs to
the school and its families; the Excel source files stay outside version control.

### VII. Modular Monolith
CampusFlow MUST be a single deployable application organised as one top-level Java package per
module (students, academic-year, leaving certificate, fees, staff, leave, payroll, fund and
audit, admin setup). A module MUST be used by other modules only through its public API
package; direct access to another module's internals or tables is prohibited. Cross-module
dependencies MUST be acyclic. No microservices or cloud dependencies.
One narrow exception: a list screen MAY read another module's table with a read-only query to show a
column that must be sorted, filtered and exported with the page (for example the student list's Total
fee, the audit log's user name). Each such read MUST be named in the feature plan's Complexity Tracking
and recorded in the decision log; it MUST NOT write, and MUST NOT create a dependency cycle.

### VIII. Every Requirement Is Tested
Every functional requirement (FR) in a feature spec MUST have at least one automated test that
references its ID. A feature is not complete until all of its FRs are covered and passing.
Calculation rules (fees, salary) MUST include tests against the reference cases in the Product
Agreement.

## Technology & Data Constraints

- Stack: Java 21, Spring Boot, PostgreSQL, Maven; packaged to run on one school computer
  with no cloud services (Docker deployment).
- Single Admin user; no role-based access control in version 1. Passwords MUST be stored
  hashed.
- Product scope and business rules are defined by `docs/agreement`; feature specs refine but
  MUST NOT contradict it without a recorded decision in `docs/decision-log.md`.
- Backups MUST be automatic and daily, with a documented restore procedure.

## Development Workflow

- Work follows Spec Kit: specify → plan → tasks → implement, on a feature branch.
- Every plan MUST include a Constitution Check against Principles I–VIII; any violation
  MUST be justified in the plan's complexity tracking and approved before implementation.
- A change is mergeable only when the build and all tests pass and review confirms compliance
  with this constitution.
- Migrations, audit behaviour, and money handling MUST be covered in review of every
  affected change.

## Governance

This constitution supersedes other practices. Amendments MUST be proposed in writing, state
the rationale and migration impact, be approved by the project owner, and be recorded by
updating this file and its version. Versioning follows semantic versioning: MAJOR for removed
or redefined principles, MINOR for added principles or materially expanded guidance, PATCH
for clarifications. All specs, plans, and reviews MUST verify compliance; unjustified
complexity or violations block merge.

**Version**: 1.1.0 | **Ratified**: 2026-10-24 | **Last Amended**: 2026-10-26

Amendment 1.0.1 was approved by the project owner on 2026-10-25 (decision log).

Amendment 1.1.0 (MINOR) was approved by the project owner on 2026-10-26 (decision log). It adds the
read-only list-column exception to Principle VII. Rationale: server-paged lists must sort, filter and
export every column in SQL (F0 FR-029 to FR-033), so a column owned by another module cannot be added
afterwards. Migration impact: none; existing code already does this once (audit user name), and F2 adds the
Total fee column.
