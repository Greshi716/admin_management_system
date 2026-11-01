# Data Model: Year Change and Admissions (F4)

One migration, `V9__year_change.sql`. Every foreign key is `ON DELETE RESTRICT`. No existing table changes shape. Money columns are `NUMERIC(12,2)`.

## Existing tables used (unchanged)

| Table | Used for |
|---|---|
| `academic_years`, `divisions`, `standards`, `student_categories` | copy source and target, next standard, stream |
| `fee_year_status`, `fee_rows`, `fee_accounts` | copied (draft), read for the new fee, new account created through `ClassRecordFees.classRecordAdded` |
| `students`, `student_enrollments` | promotion writes the old record's `year_status` and inserts the new record; Left sets `students.status` |
| `fee_brought_forward` | the single amount per student: `amount` is increased at promotion |
| `v_fee_position`, `v_earlier_pending` | read: unpaid year fee of the leaving year, pending after carry |
| `audit_log` | every write below |

## Changed tables (in `V9`, additive only)

| Table | Change | Why |
|---|---|---|
| `student_enrollments` | add `stream TEXT NULL` with CHECK `stream IN ('Science','Commerce')` | Std 11 and 12 are one row each, so the stream belongs to the class record (owner, 2026-10-31) |
| `standards` | add `uses_stream BOOLEAN NOT NULL DEFAULT false` | marks the standards that need a stream (Std 11, 12); set by the admin in Setup. `stream` (F0) stays, unused here |

Existing rows keep `stream` null; the admin can set it from the profile's class record dialog for current Std 11 and 12 students.

## New tables

### promotion_runs (append-only)

One saved promotion of one standard.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK |
| from_year_id, to_year_id | BIGINT | FK `academic_years` |
| standard_id | BIGINT | FK `standards`, the standard promoted from |
| done_by | BIGINT | FK `users` |
| done_at | TIMESTAMPTZ | |
| promoted_count, detained_count, left_count | INT | |
| carried_total | NUMERIC(12,2) | sum of carried amounts of the run |

### promotion_run_items (append-only)

One row per student of a run. Basis for audit lookups and for the developer correction.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK |
| run_id | BIGINT | FK `promotion_runs` |
| student_id | BIGINT | FK `students` |
| from_enrollment_id | BIGINT | FK `student_enrollments`, **UNIQUE** (a record is promoted once) |
| to_enrollment_id | BIGINT NULL | FK; null for Left |
| outcome | TEXT | `PROMOTED`, `DETAINED`, `LEFT` (CHECK) |
| to_standard_id, to_division_id, to_category_id | BIGINT NULL | what the admin saved |
| carried_amount | NUMERIC(12,2) | the unpaid year fee carried (0 for Left or fully paid) |
| brought_forward_before, brought_forward_after | NUMERIC(12,2) | the student's amount around the carry |

Triggers refuse UPDATE, DELETE and TRUNCATE on `promotion_runs`, `promotion_run_items` and `promotion_corrections`, as for receipts.

### promotion_corrections (append-only)

Written only by `correct_promotion` (developer, `psql`). Columns: `id`, `item_id` (FK, one correction per item, UNIQUE), `reason` (not empty), `done_by` (text, not empty), `done_at`, `amount_reversed NUMERIC(12,2)`.

### admission_import_batches

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK; the "import batch" number |
| file_name | TEXT | name only, the file itself is not stored |
| uploaded_by | BIGINT | FK `users` |
| uploaded_at | TIMESTAMPTZ | |
| rows_total, rows_added, rows_skipped, rows_invalid | INT | the upload result |

### admission_requests

The agreement's `admission_requests` plus what review needs.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT identity | PK |
| submitted_at | TIMESTAMPTZ NULL | form Timestamp; null if the file has no such column |
| form_data | JSONB | every STU-01 field except GR No. and photo, as read from the file; Aadhaar and religion/caste are masked in lists and audit |
| import_batch_id | BIGINT | FK |
| import_key | TEXT | `submitted_at` + lower(names) + dob; **UNIQUE** (re-upload skips) |
| duplicate_flag | BOOLEAN | rule of FR-032 at import |
| duplicate_of | JSONB NULL | what matched: `{students:[ids], requests:[ids]}` |
| status | TEXT | `Pending`, `Approved`, `Rejected` (CHECK) |
| reviewed_by | BIGINT NULL | FK `users` |
| reviewed_at | TIMESTAMPTZ NULL | |
| remarks | TEXT NULL | required on Reject |
| student_id | BIGINT NULL | FK `students`, set on Approve |
| version | BIGINT | optimistic lock |

CHECKs: `status = 'Approved'` implies `student_id` and `reviewed_by` not null; `status = 'Rejected'` implies `remarks` not blank. A trigger refuses any change once the status is Approved or Rejected (FR-035). Index on `status` for the Pending count.

## State changes

```text
Class record (old year):  Studying --promote--> Promoted
                          Studying --untick---> Detained
                          Studying --Left-----> Left        (student.status also Left)
Class record (new year):  created as Studying (promoted and detained students only)
Admission request:        Pending --approve--> Approved   (student + class record + fee account)
                          Pending --reject---> Rejected   (remark required)
```

## Carry-forward rule (FR-020 to FR-022)

For each promoted or detained student, with `E` the old enrollment:

```text
unpaid_year_fee = v_fee_position.pending_year_fee(E)        -- 0 when the fee is "not set"; registration never read
new_amount      = fee_brought_forward.amount (0 if none) + unpaid_year_fee
```

If `unpaid_year_fee = 0` nothing is written. Otherwise `fee_brought_forward.amount := new_amount` (insert if absent), audited with the old amount, the new amount and the carried figure. Valid previous-years receipts keep counting against `amount` (F3), so the pending the student sees is `new_amount − previous-years paid`. Worked example: fee 12,000.00, paid 9,000.00, no earlier amount: carried 3,000.00, amount 3,000.00, pending 3,000.00. Same student with an earlier amount of 5,000.00 of which 2,000.00 was paid: amount 8,000.00, paid 2,000.00, pending 6,000.00 (3,000.00 old and 3,000.00 new).

## Validation rules

- Promote: the new year is current, a previous year exists, the new year's fees are Finalised, every ticked student has a division of the target standard in the new year and a fee row (amount not "not set") for the target standard and category, a stream (Science or Commerce) when the target standard has `uses_stream`, and none otherwise.
- Upload: required headers present; per-row rules in research R7.
- Approve: GR No. not used (the F1 reservation table `student_gr_numbers` decides), year is the current or a later year with a division chosen, category set.

## Audit

| Event | Area | Action | Content |
|---|---|---|---|
| Year created with copy | Master data, and Fees | Create | counts of copied divisions; every copied fee row with its amount, registration fee and due date |
| Student promoted, kept back, left | Students | Edit | old and new standard, division, category, year status |
| Carry-forward | Fees | Edit | student, old and new year, old amount, carried, new amount (unmasked, as F3) |
| Upload | Students | Create | batch id, counts |
| Approve, Reject | Students | Create, Edit | request id, student id, remark |
| Developer correction | Students and Fees | Edit | names who did it, reason, amount reversed |
