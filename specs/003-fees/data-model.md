# Data Model: Fee Structure and Student Fee Accounts (F2)

Created by `V7__fees.sql` (V1 to V6 are not edited; V6 is F1's `V6__students.sql`). Conventions as F0 and F1: `BIGINT GENERATED ALWAYS AS IDENTITY` keys, `TIMESTAMPTZ`, `version BIGINT NOT NULL` for optimistic locking, every foreign key `ON DELETE RESTRICT` (this is what makes a standard, category or year used by a fee row "in use" under F0 FR-020). All money is `NUMERIC(12,2)` with CHECK `>= 0`. Field codes follow agreement sections 4.2 and 4.4; additions are marked **(added)**.

## fee_year_status (added)

One row per academic year that has a fee structure.

| Column | Type | Rules |
|---|---|---|
| academic_year_id | bigint | PK, FK `academic_years` |
| status | text | required, default `Draft`, CHECK in (`Draft`, `Finalised`) |
| finalised_at | timestamptz | null while Draft |
| version | bigint | optimistic lock; also the row that is locked during finalise and revise |

A year with no row is Draft with no rows. The row is created when the first fee row is added or when the year is finalised. Status goes `Draft` -> `Finalised` once; there is no way back (Revise keeps it Finalised).

## fee_rows (agreement: Fee Structure `fee_structures`)

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| academic_year_id | bigint | required, FK `academic_years` |
| standard_id | bigint | required, FK `standards` |
| category_id | bigint | optional, FK `student_categories`; null = all categories of the standard |
| amount | numeric(12,2) | required, `>= 0` (the yearly fee) |
| registration_fee | numeric(12,2) | optional, `>= 0` |
| due_date | date | optional |
| version | bigint | optimistic lock |

Unique: `(academic_year_id, standard_id, category_id)` for rows with a category, and `(academic_year_id, standard_id)` for rows without one (two partial unique indexes, because a null does not compare equal). Index `(academic_year_id)`. An inactive standard or category is not offered for new rows (checked in the service through `MasterDataLookup`); an existing row keeps it.

## fee_accounts (agreement: Student Fee Account `fee_assessments`)

One row per class record of a finalised year. Rows are created or updated, never deleted.

| Column | Type | Rules |
|---|---|---|
| enrollment_id | bigint | PK, FK `student_enrollments` |
| academic_year_id | bigint | required, FK `academic_years` (copied from the class record, for year-wide updates and counts) |
| amount | numeric(12,2) | null = "Fee not set"; otherwise `>= 0` |
| registration_fee | numeric(12,2) | null = no registration fee |
| due_date | date | optional; the due date of the row the amount came from |
| updated_at | timestamptz | required |

State of a class record's fee, derived: year Finalised and `amount` set -> **fee set**; year Finalised and `amount` null -> **Fee not set**; year not Finalised (no account row) -> **Fee structure not finalised**. The agreement's `carried_forward` flag is not a column here: earlier years' dues are the student's brought-forward amount (below), as decided on 2026-10-26.

## fee_brought_forward (added)

Dues of earlier years, one amount per student.

| Column | Type | Rules |
|---|---|---|
| student_id | bigint | PK, FK `students` |
| amount | numeric(12,2) | required, `>= 0` |
| note | text | optional, CHECK `char_length(note) <= 200` |
| updated_at | timestamptz | required |
| version | bigint | optimistic lock |

"Clear" stores 0 and no note; the row is never deleted.

## Audit

No new table and no change to the audit table. Area `Fees`; entities `FeeRow#id`, `FeeStructure#yearId`, `BroughtForward#studentId`. Entries hold old and new values (only changed fields); fee amounts and the note are stored in full (they are not in the masked field list).

| What happened | Action | Entity | Changes (besides old and new) | Reason |
|---|---|---|---|---|
| Row added, edited, deleted in a Draft year | Create / Edit / Delete | `FeeRow#id` | | |
| Row added, edited, deleted in a Finalised year (revise) | Create / Edit / Delete | `FeeRow#id` | `studentsRecalculated` | `Revise` |
| Year finalised | Finalise | `FeeStructure#yearId` | `feeAccountsCreated`, `rows` | |
| Brought-forward amount or note changed | Create (first) / Edit | `BroughtForward#studentId` | | |

## Relationships

```text
academic_years 1 -- 0..1 fee_year_status
academic_years 1 -- * fee_rows * -- 1 standards ; fee_rows * -- 0..1 student_categories
student_enrollments 1 -- 0..1 fee_accounts          (only for a finalised year)
students 1 -- 0..1 fee_brought_forward
```

## State and rules

- **Draft**: rows are added, edited and deleted directly; no `fee_accounts` exist for the year.
- **Finalise** (once, with confirmation, at least one row), in one transaction: lock the status row, set `Finalised`, create a `fee_accounts` row for every class record of the year (FR-006), write the Finalise audit entry.
- **Revise** (Finalised year, each row change on its own): needs `confirm=true`; in one transaction: lock the status row, apply the row change, recompute every class record's account (update only those whose values differ), write the audit entry with `studentsRecalculated` = number of class records of the year.
- **Class record added** to a finalised year (registration or any later record): the account is created in the same transaction. A class record of a Draft year gets no account.
- **Which row applies** to a class record `(standard, category)`: the row with that standard and category; else the row with that standard and no category; else none. Registration fee: that row's if it has one, else the blank-category row's, else none. Due date: from the row the amount came from.
- **Totals** (computed, not stored): total fee = amount + registration fee (each 0 when absent); total due (profile) = total fee + brought-forward. When the state is not "fee set", the total fee is empty and the total due is the brought-forward amount alone.
- **Lock**: while the year's status is Finalised, a class record of that year cannot change standard or category (F1's `student_enrollments` rules otherwise unchanged).
