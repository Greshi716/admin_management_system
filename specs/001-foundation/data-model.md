# Data Model: Foundation (F0)

Schema is created by Flyway migration `V2__foundation.sql` (V1 is the empty, already-applied
baseline and is not edited). Table and column codes follow agreement section 4.2 and 4.8;
additions are marked **(added)**.

Conventions: primary keys are `BIGINT GENERATED ALWAYS AS IDENTITY`; timestamps are
`TIMESTAMPTZ`; every editable table has `version BIGINT NOT NULL` for optimistic locking
(FR-046). No money columns in F0.

## users

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| name | text | required |
| username | text | required, unique |
| password_hash | text | required, BCrypt, never logged or audited |
| role | text | required, only value `ADMIN` (CHECK) |
| active | boolean | default true |
| last_login | timestamptz | set on each successful sign-in (FR-005) |
| password_changed | boolean | **(added)** false until the admin changes the generated password (change-password popup, FR-009) |
| failed_attempts | int | **(added)** consecutive failures, reset on success |
| locked_until | timestamptz | **(added)** set when `failed_attempts` reaches 5; 5 minutes (FR-044) |
| version | bigint | optimistic lock |

Created at first run only when the table is empty (FR-003).

## academic_years

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| name | text | required, unique (e.g. 2026-27) |
| start_date | date | required |
| end_date | date | required, `CHECK (end_date > start_date)` (FR-011) |
| version | bigint | |

Overlap rule: `EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&)` (FR-011).

Current year: derived, not stored. It is the year whose dates contain today's date (school local
date, zone Asia/Kolkata). The overlap constraint means at most one year can match; when none matches
there is no current year (FR-012, FR-013). Academic years have no inactive status (FR-020).

## standards

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| name | text | required, unique (FR-015) |
| display_order | int | required; default list order |
| stream | text | optional, `CHECK IN ('Science','Commerce')` |
| active | boolean | **(added)** default true |
| version | bigint | |

## divisions

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| academic_year_id | bigint | required, FK academic_years |
| standard_id | bigint | required, FK standards |
| name | text | required |
| active | boolean | **(added)** default true |
| version | bigint | |

Unique `(academic_year_id, standard_id, name)` (FR-017). FKs are `ON DELETE RESTRICT`, so a
referenced year or standard cannot be deleted (FR-020).

## student_categories

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| code | text | required, unique |
| name | text | required |
| affects_fees | boolean | required, no default (FR-019) |
| active | boolean | **(added)** default true |
| version | bigint | |

Seeded by the migration: O/S, CEW, W, STAFF, S STAFF, RTE, CASUAL (FR-018). The seed sets
`affects_fees` explicitly for each; the actual flag values are NOT in the agreement and must be
confirmed by the school (see Open point below).

## audit_log

| Column | Type | Rules |
|---|---|---|
| id | bigint | PK |
| at | timestamptz | required, set by the database |
| user_id | bigint | nullable FK users (null for a failed sign-in with an unknown username) |
| username_attempted | text | **(added)** set for failed sign-ins so the attempt is traceable |
| area | text | **(added)** `Security`, `Master data` (later `Fees`, `Payroll`, `Fund`) |
| action | text | `Create`, `Edit`, `Delete`, `Login`, `Export`; reserved `Cancel`, `Finalise`, `Import` |
| entity | text | record type and ID (e.g. `academic_years#3`, `Session`) |
| changes | jsonb | old/new values, masked per FR-025; auth event type for Security rows |
| reason | text | optional; required later for cancellations |

Append-only: a trigger raises an exception on UPDATE and DELETE (FR-024). Indexes: `(at)`,
`(area, action, at)`.

## Relationships

```text
academic_years 1 ── * divisions * ── 1 standards
users 1 ── * audit_log
student_categories (stand-alone in F0; used by fee structure and students later)
```

## Validation rules (service level)

- Required fields per FR-010, FR-014, FR-016, FR-019; Bean Validation on request DTOs.
- Cross-row rules (overlap, uniqueness) are database constraints; the service
  translates violations into clear messages (409/422 with field details).
- Stale `version` on save returns 409 with the latest record (FR-046).
- Sensitive fields: none in F0 master data; `password_hash` is never
  placed in `changes`.

## Open point

- The agreement lists seven category codes but not which of them have `affects_fees = true`.
  The spec makes the flag required with no default. Decided (decision log): seed all seven as
  `false` and let the admin set them, since no financial rule may be invented.
