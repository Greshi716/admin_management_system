# Data Model: Fee Collection and Receipts (F3)

Created by `V8__receipts.sql` (V1 to V7 are not edited; V7 is F2's `V7__fees.sql`). Conventions as F0 to F2: `BIGINT GENERATED ALWAYS AS IDENTITY` keys, `TIMESTAMPTZ`, every foreign key `ON DELETE RESTRICT`, all money `NUMERIC(12,2)` with CHECK `>= 0`. Field codes follow agreement section 4.4 (Receipts); differences are marked **(changed)** or **(added)** and are listed at the end. No table here has a `version` column: nothing in it is ever updated.

## receipts (agreement: Receipts `receipts`)

One row per fee receipt of the school's book. Never updated, never deleted (trigger `trg_receipts_immutable`, research R1).

| Column | Type | Rules |
|---|---|---|
| serial_no | bigint | PK, `GENERATED ALWAYS AS IDENTITY`; the system's running number, never reused |
| receipt_no | text | required, trimmed, 1 to 20 characters; the number typed from the printed book |
| receipt_date | date | required; not after today (checked by the service with the `Clock`; a past date is allowed) |
| academic_year_id | bigint | required, FK `academic_years`; the class record's year for a current-year receipt, the chosen earlier year (a label) for a previous-years receipt |
| student_id | bigint | required, FK `students` |
| enrollment_id | bigint | FK `student_enrollments`; required for CURRENT_YEAR, null for PREVIOUS_YEARS **(added)** |
| student_name | text | required; the student's full name when the receipt was saved **(added)** |
| gr_no | text | required; the student's GR No. when the receipt was saved **(added)** |
| standard_id, division_id, category_id | bigint | FKs; the class record's values when saved. For a current-year receipt that is the class record of the current year; for a previous-years receipt it is the student's **shown** class record (the current year's, else the most recent, the rule of the Students list; owner answer 2026-10-28); null only when the student has no class record |
| standard_name, division_name | text | the names when saved (so a reprint never changes) **(added)** |
| receipt_type | text | required, CHECK in (`CURRENT_YEAR`, `PREVIOUS_YEARS`) |
| registration_paid | numeric(12,2) | required, default 0, `>= 0`; 0 for PREVIOUS_YEARS |
| fee_paid | numeric(12,2) | required, `>= 0`; the year fee for CURRENT_YEAR, the previous years' fee for PREVIOUS_YEARS |
| total | numeric(12,2) | required, `> 0`, equals `registration_paid + fee_paid` |
| total_words | text | required; English, Indian grouping, stored (research R6) |
| payment_mode | text | required, CHECK in (`CASH`, `CHEQUE`) |
| cheque_no, bank_name | text | required when CHEQUE, null when CASH |
| cheque_date | date | required when CHEQUE, null when CASH; any date |
| created_by | bigint | FK `users`; the signed-in admin ("Received by") |
| created_at | timestamptz | required |

CHECK constraints (the money rules in the database too, constitution I):

- `ck_receipts_total`: `total = registration_paid + fee_paid` and `total > 0`.
- `ck_receipts_current`: for CURRENT_YEAR `enrollment_id IS NOT NULL`; for PREVIOUS_YEARS `enrollment_id IS NULL`, `registration_paid = 0` and `fee_paid > 0`.
- `ck_receipts_mode`: for CHEQUE all three cheque fields are not null and not blank; for CASH all three are null.
- `ck_receipts_receipt_no`: `char_length(btrim(receipt_no)) BETWEEN 1 AND 20` and `receipt_no = btrim(receipt_no)`.

Indexes:

- `ix_receipts_receipt_no` on `upper(receipt_no)`: serves the duplicate check. Uniqueness is enforced by the trigger below, not by a unique index, because a voided receipt frees its number (research R12).
- `ix_receipts_student` on `(student_id, serial_no)`: the profile's list and the student lock queries.
- `ix_receipts_enrollment` on `(enrollment_id)` where `enrollment_id IS NOT NULL`: the sums of `v_fee_position`.
- `ix_receipts_previous` on `(student_id)` where `receipt_type = 'PREVIOUS_YEARS'`: the sums of `v_earlier_pending`.
- `ix_receipts_date` on `(receipt_date, serial_no)`: the register's default order and date range.
- `ix_receipts_mode_date` on `(payment_mode, receipt_date)` and `ix_receipts_standard_date` on `(standard_id, receipt_date)`: the register's mode and standard filters.

## receipt_marks (added; the agreement's `status` and `unclear_reason`)

One row says a receipt no longer counts as paid. Append-only (trigger `trg_receipt_marks_immutable`). A receipt with no row is Valid.

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK, identity |
| receipt_serial_no | bigint | required, FK `receipts`, **UNIQUE** (a receipt is marked at most once; one way) |
| kind | text | required, CHECK in (`CHEQUE_NOT_CLEARED`, `DEVELOPER_VOID`) |
| reason | text | required, `char_length(btrim(reason)) BETWEEN 1 AND 200` |
| amount_added_back | numeric(12,2) | required, `> 0`; the receipt's total |
| marked_by | bigint | FK `users`; null for `DEVELOPER_VOID` (the developer is named in the audit row) |
| marked_at | timestamptz | required |

Status shown to the user: no row = **Valid**; kind `CHEQUE_NOT_CLEARED` = **Cheque not cleared**; kind `DEVELOPER_VOID` = **Voided by correction**.

There is no print table: every print of a receipt is identical and prints are not recorded (owner decision, FR-018).

## Triggers

- **Immutability**: one function `receipts_reject_change()` raises `receipts and receipt_marks are append-only: <op> is not allowed`. Row triggers (`BEFORE UPDATE OR DELETE ... FOR EACH ROW`) and statement triggers (`BEFORE TRUNCATE ... FOR EACH STATEMENT`) are attached to the two tables, as `audit_log` does in V2. Tests reset the schema with `flyway.clean()` (a drop), which the triggers do not stop.
- **Receipt number** (`trg_receipts_receipt_no`, `BEFORE INSERT ON receipts`): takes `pg_advisory_xact_lock` on the upper-cased number, then refuses (SQLSTATE `23505`, constraint name `uq_receipts_receipt_no`) when a receipt with the same `upper(receipt_no)` exists that has no `DEVELOPER_VOID` mark. A voided receipt frees its number; a Cheque not cleared one does not (research R12).
- **Cheque mark** (`BEFORE INSERT ON receipt_marks`): refuses `CHEQUE_NOT_CLEARED` for a receipt whose `payment_mode` is not `CHEQUE`.

## Views (owned by `fees`; the only fee data the Students list reads besides `fee_accounts`)

**`v_valid_receipts`**: `SELECT r.* FROM receipts r WHERE NOT EXISTS (SELECT 1 FROM receipt_marks m WHERE m.receipt_serial_no = r.serial_no)`. The one SQL definition of a valid receipt.

**`v_fee_position`** (one row per `fee_accounts` row):

| Column | Meaning |
|---|---|
| enrollment_id | the class record (key) |
| academic_year_id | its year |
| year_fee | `fee_accounts.amount` (null = fee not set) |
| registration_fee | `fee_accounts.registration_fee` (null = none) |
| paid_year_fee | sum of valid `fee_paid` of CURRENT_YEAR receipts of the record, 0 when none |
| paid_registration | sum of valid `registration_paid`, 0 when none |
| pending_year_fee | `GREATEST(year_fee - paid_year_fee, 0)`; null when year_fee is null |
| pending_registration | `GREATEST(registration_fee - paid_registration, 0)`; 0 when there is none |
| excess_paid | `GREATEST(paid_year_fee - year_fee, 0) + GREATEST(paid_registration - COALESCE(registration_fee, 0), 0)` |

**`v_earlier_pending`** (one row per `fee_brought_forward` row): `student_id`, `amount` (the brought-forward amount), `paid` (sum of valid `fee_paid` of PREVIOUS_YEARS receipts of the student), `pending = amount - paid` (never negative: FR-025 and the receipt limit keep `paid <= amount`; a test checks it).

## Function `void_receipt(serial_no bigint, reason text, done_by text)` (developer only)

In one transaction: refuses a missing receipt, a receipt that already has a mark, an empty reason and an empty `done_by` (so "who" is always recorded; the audit row has no `user_id` because the developer is not a user); inserts a `DEVELOPER_VOID` mark (amount added back = the receipt's total) and one `audit_log` row (area `Fees`, action `Edit`, entity `Receipt#serialNo`, changes `status` old `VALID` new `VOIDED`, `by` = `done_by`, `amountAddedBack`; reason = `reason`). The application never calls it; a test scans the main sources for the name. Used by `docs/runbooks/correct-receipt.md`.

## Balance of a student (computed, never stored)

For the class record `E` of the current year and the student `S` (the numbers the receipt form, the profile and the Students list show):

- **Year fee due** = `v_fee_position.pending_year_fee`; **registration fee due** = `pending_registration`; **excess paid** = `excess_paid` (after a revise lowers a fee; F2 FR-004).
- **Earlier years pending** = `v_earlier_pending.pending` (0 when the student has no brought-forward row).
- **Total fee** (profile, FR-014) = year fee + registration fee + brought-forward amount; **Paid** = valid `fee_paid` + `registration_paid` of the current-year record + valid previous-years `fee_paid`; **Pending** = year fee due + registration fee due + earlier years pending.
- **Students list** (FR-015, plan Q7): Total fee (F2, year + registration), Paid (the shown record's valid current-year receipts), Pending (the shown record's pending), Earlier years pending, Paid up to, Fee status. Fee status: **Paid** when pending and earlier years pending are both 0, **Partly paid** when something is paid and something is pending, **Unpaid** when nothing is paid and something is pending; "Fee not set" and "Not finalised" (F2) when there is no amount.
- **Paid up to** (profile and list only; owner answer to Q4) = `PaidUpTo` of the current valid `paid_year_fee` (research R4), the **last paid day**: year start + days paid − 1; previous-years receipts do not enter it. It is worked out when shown and is not a column of any table.
- **Balance token** = `r<receipts of S>.m<marks of S>.a<epoch micros of fee_accounts.updated_at of E, 0 if none>.b<fee_brought_forward.version, 0 if none>`; opaque to the client (research R3).

## State and rules

- **Create a current-year receipt**, in one transaction after the student lock: needs a current academic year, a class record of that year, a finalised structure with an amount for the record, brought-forward pending 0; `registration_paid <= pending_registration`, `fee_paid <= pending_year_fee`, at least one above 0; registration fee only when the account has one; receipt date not after today; the receipt number unused; then insert, write the audit entry.
- **Create a previous-years receipt**: the student has brought-forward pending above 0; the chosen year starts before the current year; `fee_paid` above 0 and not above the pending; no registration fee; no "Paid up to".
- **Mark Cheque not cleared**: after the student lock and the token check; receipt is CHEQUE and has no mark; insert the mark; write the audit entry with the student's "Paid up to" before and after. Nothing in `receipts` changes.
- **A cheque paid again** is a new receipt with a new receipt number (the marked receipt keeps its number); the marked receipt stays.
- **A voided receipt** is corrected by a new receipt that may use its number.
- **Left student**: may still pay (no status check). **No class record in the current year**: only a previous-years receipt.
- **Snapshot**: the receipt keeps the student's name, GR No., standard and division names as they were when saved.

## Relationships

```text
academic_years 1 -- * receipts * -- 1 students
student_enrollments 0..1 -- * receipts          (current-year receipts only)
receipts 1 -- 0..1 receipt_marks
fee_accounts 1 -- 1 v_fee_position ; fee_brought_forward 1 -- 1 v_earlier_pending
users 1 -- * receipts, receipt_marks (nullable for a developer void)
```

## Audit

No new table and no change to the audit table. Area `Fees`; entity `Receipt#serialNo`. Money and cheque fields are stored in full (they are not in `SensitiveMasker.MASKED`; a test proves it).

| What happened | Action | Entity | Changes | Reason |
|---|---|---|---|---|
| Receipt saved | Create | `Receipt#serialNo` | `new`: receiptNo, studentId, type, mode, registrationPaid, feePaid, total, academicYearId, chequeNo and bankName (Cheque) | |
| Cheque marked not cleared | Edit | `Receipt#serialNo` | status old `VALID` new `CHEQUE_NOT_CLEARED`, amountAddedBack, the student's paidUpToBefore and paidUpToAfter (worked out for the entry) | the typed reason |
| Receipt voided by the developer | Edit | `Receipt#serialNo` | status old `VALID` new `VOIDED`, amountAddedBack, by | the typed reason |
| Register exported | Export | `Receipt register` | filters, row count (the F0 export entry) | |

## Differences from the agreement's data section

Agreement 4.4 puts `status` (two values: Valid, Cheque not cleared), `unclear_reason`, `unclear_by`, `unclear_at` and `paid_up_to` on the receipt; has `class_snapshot` as one field; has `receipt_type` as "Auto" and the cheque fields as "Optional"; has `academic_year` as "Auto"; and has no print table. The agreement's section 4.1 data diagram shows receipts linked to the student and the year only. This feature differs on purpose: the status has a third value, Voided by correction, held in `receipt_marks`; the class snapshot is ids plus names; `receipt_type` is chosen by the admin; the cheque fields are required for a Cheque receipt (spec FR-006); `academic_year_id` is the chosen earlier year for a previous-years receipt; the diagram gains `student_enrollments`, `receipt_marks` and two views. The other differences: (decision log entry to be written with the first task, as owner decisions of 2026-10-28): the receipt has no status column and the mark is its own row (R1); the receipt has no `paid_up_to` (it is shown on the student, R4); `serial_no` is the primary key; `enrollment_id` and the snapshot names are added; the receipt number is unique by trigger, a voided receipt freeing it (R12); there is no print record and no DUPLICATE mark (R8); `registration_paid` and `fee_paid` keep their names, and `fee_paid` carries the previous years' fee for that type. `academic_year` is the class record's year or, for a previous-years receipt, the earlier year chosen. `created_by` is "Received by".
