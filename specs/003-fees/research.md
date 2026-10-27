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
- **Frontend rule**: the browser formats amounts to two decimals with Indian digit grouping and never adds them; every total (total fee, total due) comes from the server, because a JSON number loses trailing zeros when parsed and a sum in floating point is not exact.

## R4. Revise confirmation

- **Decision**: in a Finalised year, `POST`, `PUT` or `DELETE` on a fee row without `confirm=true` changes nothing and returns 409 with code `REVISE_CONFIRMATION_REQUIRED` and `current: { studentCount }`. The screen shows its warning with that count; on confirm it repeats the request with `confirm=true`. In a Draft year `confirm` is ignored.
- **Rationale**: the rule "nothing is saved until the admin confirms" (FR-004) is then true even for a client that skips the screen, and the count comes from the server in the same transaction scope it will use.
- **Alternatives**: a separate `GET /impact` call plus a flag (two calls can disagree and a client can skip the first); a confirmation token (more machinery than one admin needs).
- **Codes and stale structure**: F1's `ConflictException` carries only a field and a message, so a new `CodedConflictException` (code, field, message, optional `current`) is added to `shared.web` with a handler in `ApiExceptionHandler` that fills `ApiProblem.code` and `ApiProblem.current`. It carries `REVISE_CONFIRMATION_REQUIRED` (`current` = `{ studentCount }`) and `FEE_STRUCTURE_FINALISED` (R8). Every row request also carries `structureVersion`; every row change bumps `fee_year_status.version`, so a window whose screen is out of date (for example the year was finalised elsewhere) gets `STALE_VERSION` with the latest structure before any revise warning is shown. A duplicate row (two partial unique indexes) is pre-checked in the service and the index names are added to the `KNOWN` map of `ApiExceptionHandler`, so the message is shown beside the standard or category field.

## R5. Audit

- **Decision**: area `Fees`. Entities `FeeRow#id`, `FeeStructure#yearId`, `BroughtForward#studentId`. Actions: Create, Edit, Delete for rows (Draft: no reason; Finalised: reason `Revise` and `studentsRecalculated` in the changes), `Finalise` for finalising (changes: `studentsRecalculated`, `accountsChanged`, `rows`), Create or Edit for the brought-forward amount (old and new amount in full; for the note only `noteChanged`, never the text). Only changed fields are written (the `MasterDataAudit` pattern); an edit that changes nothing writes nothing. `fees/FeesAudit` mirrors `StudentAudit`.
- **Rationale**: the audit table's CHECK allows Create, Edit, Delete, Finalise already; F0 FR-027 says later modules need no structural change. A reason column exists.
- **Alternatives**: add an action `Revise` (a migration on the audit table and a change to F0's filter); one entry per student recalculated (thousands of entries, no extra information).
- **Revise in the log**: the entry has the reason `Revise` and, besides the row's old and new values, `studentsRecalculated` (every class record of the year) and `accountsChanged` (accounts whose values actually changed). There is no entry per student: the accounts are derived from the rows, so the row values and the two counts are enough to rebuild any account's before and after. A separate action `Revise` is not added (it would change the audit table's CHECK list, against F0 FR-027); a revise is found by area Fees and the reason.

## R6. Which fee applies, and the account states

- **Decision**: `FeeSelection` is a pure function of (rows of the year, standardId, categoryId): the row with that standard and category, else the row with that standard and no category, else none. The registration fee is that row's registration fee when it has one, else the blank-category row's, else none (spec clarification). A `fee_accounts` row stores `amount` (null = "Fee not set"), `registration_fee` (null = none) and `due_date` (taken from the amount's row). States shown: no account row and a Draft year = "Fee structure not finalised"; account with null amount = "Fee not set".
- **Rationale**: one small rule that can be unit-tested against every example in the spec, with no database.
- **Alternatives**: computing the fee on every read (the stored amount is what F3 will collect against and must not move except by a revise); storing a flag column for "not set" (null already says it).

## R7. Recalculation, size and concurrency

- **Decision**: finalise and every confirmed revise run in one transaction: take `SELECT ... FOR UPDATE` on the year's `fee_year_status` row, load the year's rows and class records, compute every account in memory with `FeeSelection`, and insert or update only accounts whose values differ (batch upsert). The audit entry's count is every class record of the year. Adding a class record to a finalised year (`classRecordAdded`) takes `FOR SHARE` on the status row, so it waits for a running finalise or revise and then sees the final state.
- **Rationale**: 2,000 records is a few thousand small statements at most; no set-based SQL is needed, which keeps the rule in one place (R6). One admin makes a real race unlikely; the locks make it safe anyway.
- **Alternatives**: SQL `INSERT ... SELECT` with joins (duplicates the selection rule in SQL); recalculating only the standards touched by a row (the same result, but the spec and audit count say every class record, and a full pass is simpler to prove correct). Measured in the first-task spike; the fallback is JDBC batch statements.
- **Locks**: the status row is read with `PESSIMISTIC_WRITE` (Spring Data `@Lock`, `FOR UPDATE`) by finalise and every row change, and with `PESSIMISTIC_READ` (`FOR SHARE`) by `classRecordAdded`. The status row is created together with the first fee row, so it exists whenever there is anything to finalise; a year with no status row has no structure and `classRecordAdded` creates nothing. First task 2 checks that the generated SQL is as stated and works with `@Version`; first task 3 adds a test that a registration during a finalise ends with an account.

## R8. The lock on standard and category

- **Decision**: `StudentService.changeEnrollment` asks `ClassRecordFees.isLocked(yearId)` and, when the year is locked and the standard or category differs from the record's, throws `CodedConflictException` with code `FEE_STRUCTURE_FINALISED` (409, see R4) before any change. A change that keeps both is allowed (division only). `EnrollmentDto` gets `feeLocked` so the class record dialog shows both fields as fixed with the message before the admin submits. Registration is unaffected (it creates the first record of the current year; if that year is finalised the account is created by the hook).
- **Rationale**: server rule plus screen hint (FR-008); no new endpoint.
- **Alternatives**: a database trigger (hides the rule from the code and the message); disabling only in the screen (not enforced).

## R9. List columns

- **Decision**: two new columns in `StudentTable`: `totalFee` (number, `amount + coalesce(registration_fee, 0)` of the shown record's account, null when none) and `feeState` (internal text: `NOT_SET` when the account exists with a null amount, else null). The shown record is the same one F1 uses (current year, else most recent). Both appear in the Excel export (`totalFee` as a number with two decimals; `feeState` as "Fee not set" or empty). The screen shows "Fee not set", "Not finalised" (no account) or the amount.
- **Rationale**: same technique as F1's other columns; see Complexity Tracking in the plan for the table access.
- **Alternatives**: see the plan.
- **Excel**: `ExcelExporter` writes `BigDecimal` as text (`toPlainString`) and any other `Number` with `longValue()`. F2 changes it to write `BigDecimal` as a numeric cell with data format `0.00` and other numbers without truncating, so exported amounts can be summed. The F0 and F1 export tests are updated in the same task. `totalFee` is an expression typed `BigDecimal` so the filter parser (`toNumber`) and the sort work on decimals.

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

## Starting point (T001, 2026-10-27)

- Local `main` brought up to date with `git fetch origin main:main` (fast-forward; `main` and `origin/main` are both `ece1410`, the F1 merge). `ece1410` is an ancestor of `003-fees`. `V6__students.sql` and `BE/students/` exist.
- `scripts/verify.sh` on the branch before any F2 code: backend 310 tests passed (0 failures, 0 errors); frontend unit 150 passed (20 files); Playwright 107 passed, 1 failed.
- The one Playwright failure is `fr-coverage-check.spec.ts` "FR-036: every requirement of the foundation spec is covered, in strict mode", and it fails only because of the coverage line below (it runs the strict script).
- Coverage line: `FR coverage: 46 of 46 covered` (F0), `FR coverage on branch 003-fees: 0 of 14 covered`, uncovered FR-001 to FR-014. This is RED as expected: no F2 test exists yet. It turns green when T027 passes.

## Plan checks (T002, 2026-10-27; PostgreSQL 15, Hibernate as in the project; throwaway test `FeePlanChecksIT`, deleted)

- **(a) Masking (R5)**: `SensitiveMasker.MASKED` holds none of `amount`, `registrationFee`, `note`, `noteChanged`, `studentsRecalculated`, `accountsChanged`. `sanitize(changes, MASKED)` returns a map with exactly those keys and values unchanged. Nothing to add for fee fields; the note text is kept out of the log by `FeesAudit` writing only `noteChanged: true` (T008, T022).
- **(b) `ApiProblem.current` (R4)**: a `StaleVersionException` carrying `Map.of("studentCount", 5)` goes through `ApiExceptionHandler` as `{"title":"Conflict","status":409,"detail":"...","code":"STALE_VERSION","current":{"studentCount":5}}`. A map is serialised as given, so `CodedConflictException` (T005) can use the same `withCurrent(...)` for `REVISE_CONFIRMATION_REQUIRED`.
- **(c) Locks (R7)**, real SQL from `org.hibernate.SQL`:
  - `PESSIMISTIC_WRITE` -> `... where ay1_0.id=? for no key update of ay1_0` (**not** `for update` as R7 says).
  - `PESSIMISTIC_READ` -> `... where ay1_0.id=? for share of ay1_0`.
  - Two connections (second one with `set local lock_timeout = '1000ms'`, lock-not-available SQLState 55P03 means it waited): write blocks read: yes; write blocks write: yes; read blocks write: yes; read blocks read: no. This is the behaviour R7 needs: a registration (share) waits for a finalise or revise (write) and the reverse, while two registrations do not wait for each other.
  - With `@Version`: a row loaded with `PESSIMISTIC_WRITE` and changed raises its version by 1 on flush, and a stale detached copy still fails on save with `jakarta.persistence.OptimisticLockException` (Spring repositories translate it to `ObjectOptimisticLockingFailureException`, which the handler already maps to `STALE_VERSION`).
  - Wording to use from now on: "`FOR NO KEY UPDATE`" for the write lock. It does not block `FOR KEY SHARE` (taken by foreign-key checks), which is fine here because no table has a foreign key to `fee_year_status`.
