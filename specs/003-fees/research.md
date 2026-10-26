# Research: Fee Structure and Student Fee Accounts (F2)

No item in the Technical Context was left as NEEDS CLARIFICATION. These are the design decisions, each with the alternatives rejected.

## R1. Where the fee structure lives and what "Draft" means

- **Decision**: module `fees` with tables `fee_year_status` (one row per academic year that has a structure: `Draft` or `Finalised`, with a `version`), `fee_rows` (the rows) and `fee_accounts` (one per class record of a finalised year). A year with no `fee_year_status` row is Draft with no rows. The status row is created when the first row is added or when the year is finalised.
- **Rationale**: the status is a fact about the year, not about a row, and the lock and the stale-save check need one place to attach to.
- **Alternatives**: a status column on every row (can disagree between rows); a status on `academic_years` (that table belongs to F0 master data; the constitution keeps modules apart).

## R2. Breaking the students <-> fees cycle

- **Decision**: `fees` depends on `students.api` (new public package: `EnrollmentLookup` returns the class records of a year as `(enrollmentId, studentId, standardId, categoryId)`, the count, and a student's shown record; `StudentLookup.exists`). `students` depends only on a small interface in `shared`, `ClassRecordFees`, implemented by `fees`: `isLocked(yearId)` (year is Finalised) and `classRecordAdded(enrollmentId, yearId, standardId, categoryId)` (creates the fee account in the caller's transaction). This is the pattern already used for `CurrentAcademicYearProvider`.
- **Rationale**: keeps the order `shared <- audit <- masterdata <- students <- fees` acyclic without events or a second transaction. `ModuleDependencyTest` is extended: `fees -> students.api` is allowed; `students -> fees` is not.
- **Alternatives**: `students -> fees.api` and `fees -> students.api` (a cycle, forbidden); Spring application events (the account would be created after commit or in a hidden second place, and a failure would leave a class record without an account); moving fee accounts into `students` (mixes two concepts).

## R3. Money handling

- **Decision**: `BigDecimal` fields, `NUMERIC(12,2)` columns, CHECK `>= 0`. JSON carries amounts as numbers; the server rejects a value whose scale is more than 2 after `stripTrailingZeros()` and a negative value, with a field error (FR-011). Values are stored and returned with exactly two decimals (`setScale(2)`), shown with two decimals and Indian digit grouping (28,000.00). No rounding is applied anywhere; where a later feature needs one it is `RoundingMode.HALF_UP`.
- **Rationale**: the spec says "refused, not rounded"; silent rounding of a typed fee would hide a typing mistake.
- **Alternatives**: accept and round HALF_UP (hides mistakes); amounts as strings in JSON (safe but unlike the rest of the API; a 12,2 value is exact in a JavaScript number).

## R4. Revise confirmation

- **Decision**: in a Finalised year, `POST`, `PUT` or `DELETE` on a fee row without `confirm=true` changes nothing and returns 409 with code `REVISE_CONFIRMATION_REQUIRED` and `current: { studentCount }`. The screen shows its warning with that count; on confirm it repeats the request with `confirm=true`. In a Draft year `confirm` is ignored.
- **Rationale**: the rule "nothing is saved until the admin confirms" (FR-004) is then true even for a client that skips the screen, and the count comes from the server in the same transaction scope it will use.
- **Alternatives**: a separate `GET /impact` call plus a flag (two calls can disagree and a client can skip the first); a confirmation token (more machinery than one admin needs).

## R5. Audit

- **Decision**: area `Fees`. Entities `FeeRow#id`, `FeeStructure#yearId`, `BroughtForward#studentId`. Actions: Create, Edit, Delete for rows (Draft: no reason; Finalised: reason `Revise` and `studentsRecalculated` in the changes), `Finalise` for finalising (changes: `feeAccountsCreated`, `rows`), Create or Edit for the brought-forward amount. Only changed fields are written (the `MasterDataAudit` pattern); an edit that changes nothing writes nothing. `fees/FeesAudit` mirrors `StudentAudit`.
- **Rationale**: the audit table's CHECK allows Create, Edit, Delete, Finalise already; F0 FR-027 says later modules need no structural change. A reason column exists.
- **Alternatives**: add an action `Revise` (a migration on the audit table and a change to F0's filter); one entry per student recalculated (thousands of entries, no extra information).

## R6. Which fee applies, and the account states

- **Decision**: `FeeSelection` is a pure function of (rows of the year, standardId, categoryId): the row with that standard and category, else the row with that standard and no category, else none. The registration fee is that row's registration fee when it has one, else the blank-category row's, else none (spec clarification). A `fee_accounts` row stores `amount` (null = "Fee not set"), `registration_fee` (null = none) and `due_date` (taken from the amount's row). States shown: no account row and a Draft year = "Fee structure not finalised"; account with null amount = "Fee not set".
- **Rationale**: one small rule that can be unit-tested against every example in the spec, with no database.
- **Alternatives**: computing the fee on every read (the stored amount is what F3 will collect against and must not move except by a revise); storing a flag column for "not set" (null already says it).

## R7. Recalculation, size and concurrency

- **Decision**: finalise and every confirmed revise run in one transaction: take `SELECT ... FOR UPDATE` on the year's `fee_year_status` row, load the year's rows and class records, compute every account in memory with `FeeSelection`, and insert or update only accounts whose values differ (batch upsert). The audit entry's count is every class record of the year. Adding a class record to a finalised year (`classRecordAdded`) takes `FOR SHARE` on the status row, so it waits for a running finalise or revise and then sees the final state.
- **Rationale**: 2,000 records is a few thousand small statements at most; no set-based SQL is needed, which keeps the rule in one place (R6). One admin makes a real race unlikely; the locks make it safe anyway.
- **Alternatives**: SQL `INSERT ... SELECT` with joins (duplicates the selection rule in SQL); recalculating only the standards touched by a row (the same result, but the spec and audit count say every class record, and a full pass is simpler to prove correct). Measured in the first-task spike; the fallback is JDBC batch statements.

## R8. The lock on standard and category

- **Decision**: `StudentService.changeEnrollment` asks `ClassRecordFees.isLocked(yearId)` and, when the year is locked and the standard or category differs from the record's, throws `ConflictException` with code `FEE_STRUCTURE_FINALISED` (409) before any change. A change that keeps both is allowed (division only). `EnrollmentDto` gets `feeLocked` so the class record dialog shows both fields as fixed with the message before the admin submits. Registration is unaffected (it creates the first record of the current year; if that year is finalised the account is created by the hook).
- **Rationale**: server rule plus screen hint (FR-008); no new endpoint.
- **Alternatives**: a database trigger (hides the rule from the code and the message); disabling only in the screen (not enforced).

## R9. List columns

- **Decision**: two new columns in `StudentTable`: `totalFee` (number, `amount + coalesce(registration_fee, 0)` of the shown record's account, null when none) and `feeState` (internal text: `NOT_SET` when the account exists with a null amount, else null). The shown record is the same one F1 uses (current year, else most recent). Both appear in the Excel export (`totalFee` as a number with two decimals; `feeState` as "Fee not set" or empty). The screen shows "Fee not set", "Not finalised" (no account) or the amount.
- **Rationale**: same technique as F1's other columns; see Complexity Tracking in the plan for the table access.
- **Alternatives**: see the plan.

## R10. Setup screen and the prototype

- **Decision**: one screen at `/setup/fees` under a new Setup group "Fees and salary", item "Fee structure". A year picker (current year first) and a table of rows (Standard, Category or "All categories", Yearly fee, Registration fee, Due date, actions) in the prototype's card, with Add row, Finalise (Draft) or Revise (Finalised) and the status shown as a tag. "Copy from last year" and the other unbuilt Setup items stay hidden.
- **Rationale**: the agreement's Fee Structure is one row per standard and category; the prototype's grid (a column per category, one due date per standard) cannot hold a registration fee or a due date per row. The spec records this difference.
- **Alternatives**: the grid as drawn (loses fields the agreement defines); both views (more to build and test than one admin needs).

## R11. Brought-forward screen and rules

- **Decision**: `GET` and `PUT /students/{id}/brought-forward` with `{version, amount, note}`. Amount zero or more with two decimals; note optional, at most 200 characters (a CHECK too). "Clear" saves 0 and an empty note; the row is never deleted. The first save creates the row (version null in the request means "create"); an edit of a stale version returns the F0 409 `STALE_VERSION`.
- **Rationale**: matches spec FR-007 and keeps the no-delete habit.
- **Alternatives**: store it on `students` (the student edit would have to carry money and its audit would mix with personal data).

## R12. Testing approach

- **Decision**: unit tests for `FeeSelection` (category row, fallback, none, registration fee from the category row, from the blank row, none); integration tests for finalise, revise count and confirmation, accounts after registration in a finalised year, the lock, brought-forward, audit in the same transaction (a failed audit write leaves nothing), and the list column; Playwright for the screen, the warning, the class record dialog and the profile block.
- **Rationale**: constitution VIII needs every FR covered, and the calculation rules need reference cases.
