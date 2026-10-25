# Data Model: Students and Class Records (F1)

Created by `V6__students.sql` (V1 to V5 are not edited). Conventions as F0: `BIGINT GENERATED ALWAYS AS
IDENTITY` keys, `TIMESTAMPTZ`, `version BIGINT NOT NULL` for optimistic locking. No money columns.
Field codes follow agreement section 4.3; additions are marked **(added)**. All foreign keys are
`ON DELETE RESTRICT`. Nothing is ever deleted (constitution IV).

## students

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| gr_no | text | required, trimmed, unique on `lower(gr_no)` |
| sr_no | bigint | required, unique, default `nextval('student_sr_no_seq')`, never reused |
| surname | text | required |
| first_name | text | required |
| middle_name | text | optional |
| gender | text | required, CHECK in (`Male`, `Female`, `Other`) |
| dob | date | required |
| birth_place | text | optional |
| father_name | text | optional |
| mother_name | text | optional |
| mobile_1 | text | required, 10 digits (CHECK `~ '^[0-9]{10}$'`) |
| mobile_2 | text | optional, same CHECK when present |
| address | text | required |
| aadhaar | text | optional, 12 digits (CHECK), sensitive |
| religion_caste | text | optional, sensitive |
| category_id | bigint | required, FK `student_categories` (the category chosen at registration) |
| admission_date | date | required, CHECK `admission_date >= dob` (the "not after today" rule needs the school date, so it is checked in the service) |
| admission_standard_id | bigint | required, FK `standards` |
| previous_school | text | optional |
| photo_version | bigint | **(added)** null = no photo; set when a photo is added or replaced |
| status | text | required, default `Active`, CHECK in (`Active`, `Left`) |
| source | text | required, default `Manual`, CHECK in (`Manual`, `Google Form`) |
| created_at | timestamptz | **(added)** |
| version | bigint | optimistic lock |

Indexes: unique `lower(gr_no)`; unique `sr_no`; `lower(surname), lower(first_name)` (name warning);
`mobile_1`; `mobile_2`; `status`; `category_id`; `admission_standard_id`.
The list's "contains" search scans the table (fine at a few thousand rows); the indexes serve
equality, prefix and the name warning (research R3, R6). No unique index on Aadhaar (the agreement does not ask for one).

## student_gr_numbers (added)

Every GR No. ever given to a student, so a corrected number stays reserved.

| Column | Type | Rules |
|---|---|---|
| gr_key | text | PK, `lower(trim(gr_no))` |
| student_id | bigint | required, FK `students` |
| assigned_at | timestamptz | required |

## student_enrollments (agreement: Yearly Class Record)

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| student_id | bigint | required, FK `students` |
| academic_year_id | bigint | required, FK `academic_years` |
| standard_id | bigint | required, FK `standards` |
| division_id | bigint | required, FK `divisions` (must belong to the same year and standard, checked in the service through `MasterDataLookup`) |
| category_id | bigint | required, FK `student_categories` (this year's category; the one fees will use) |
| year_start_date | date | **(added)** required; copied from the academic year when the row is created; used only to find a student's most recent record (years cannot overlap, so the order cannot change) |
| year_status | text | required, default `Studying`, CHECK in (`Studying`, `Promoted`, `Detained`, `Left`); this feature only ever writes `Studying` |
| version | bigint | optimistic lock |

Unique `(student_id, academic_year_id)` (one row per student per year). Indexes:
`(academic_year_id, standard_id, division_id)`, `(academic_year_id, category_id)`.
A same-year, same-standard check across tables is not a database constraint because it spans master data tables owned by another module.

## student_photos (added)

| Column | Type | Rules |
|---|---|---|
| student_id | bigint | PK, FK `students` |
| content | bytea | required, resized JPEG (about 400 px longest side) |
| content_type | text | required, always `image/jpeg` after resizing |
| size_bytes | int | required |
| updated_at | timestamptz | required |

Removing a photo deletes this row and sets `students.photo_version` to null; the removal is audited. This is the only delete in the feature: students, class records and GR numbers are never deleted.

## Audit

No new table. `V6` adds one index, `audit_log (entity, at)`, so the profile History block reads by record id (`Student#id`, `StudentEnrollment#id`) without scanning; the append-only trigger is not affected. Entries use area `Students` (decision log, 25-10-2026), actions `Create` and `Edit` (the status change is an Edit of `status`), `Export`; entity `Student#id` or `StudentEnrollment#id`; `changes` hold old and new values with `aadhaar` and `religionCaste` masked. Photo add, replace and remove are audited as an Edit of `photo` with no image content.

## Relationships

```text
academic_years 1 ── * student_enrollments * ── 1 students 1 ── 0..1 student_photos
standards, divisions, student_categories 1 ── * student_enrollments   (foreign keys only)
students 1 ── * student_gr_numbers
```

## State and rules

- Status: `Active` -> `Left` only (clarified). A returning student is a new `students` row with a new GR No.
- Registration, in one transaction: insert student, GR key, enrollment for the current year, audit entries. If any step fails nothing is saved.
- Editing a class record changes standard, division and category of that row; it never creates a second row for the same year.
- `year_status`: `Studying` on creation; marking Left does not change it (clarified).
