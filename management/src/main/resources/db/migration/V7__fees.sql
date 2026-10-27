-- Fee structure, fee accounts and brought-forward dues (F2: FR-001, FR-006, FR-007, FR-011)
-- Fee accounts and brought-forward amounts are never deleted by the application (constitution IV);
-- every foreign key is ON DELETE RESTRICT.

-- Draft or Finalised state of a year's fee structure; the row that is locked during finalise and revise.
CREATE TABLE fee_year_status (
    academic_year_id  BIGINT      NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'Draft',
    finalised_at      TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_fee_year_status PRIMARY KEY (academic_year_id),
    CONSTRAINT fk_fee_year_status_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fee_year_status_status CHECK (status IN ('Draft', 'Finalised')),
    CONSTRAINT ck_fee_year_status_finalised_at CHECK ((status = 'Finalised') = (finalised_at IS NOT NULL))
);

-- One row per standard and category (category null = all categories of the standard).
CREATE TABLE fee_rows (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    academic_year_id  BIGINT        NOT NULL,
    standard_id       BIGINT        NOT NULL,
    category_id       BIGINT,
    amount            NUMERIC(12,2) NOT NULL,
    registration_fee  NUMERIC(12,2),
    due_date          DATE,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_fee_rows PRIMARY KEY (id),
    CONSTRAINT fk_fee_rows_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_rows_standard FOREIGN KEY (standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_rows_category FOREIGN KEY (category_id)
        REFERENCES student_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fee_rows_amount CHECK (amount >= 0),
    CONSTRAINT ck_fee_rows_registration_fee CHECK (registration_fee IS NULL OR registration_fee >= 0)
);

-- A null does not compare equal, so "one row per standard and category" needs two partial unique indexes.
CREATE UNIQUE INDEX uq_fee_rows_category ON fee_rows (academic_year_id, standard_id, category_id)
    WHERE category_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fee_rows_no_category ON fee_rows (academic_year_id, standard_id)
    WHERE category_id IS NULL;
CREATE INDEX ix_fee_rows_year ON fee_rows (academic_year_id);

-- One per class record of a finalised year. amount null = "Fee not set"; registration_fee null = none.
CREATE TABLE fee_accounts (
    enrollment_id     BIGINT        NOT NULL,
    academic_year_id  BIGINT        NOT NULL,
    amount            NUMERIC(12,2),
    registration_fee  NUMERIC(12,2),
    due_date          DATE,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_fee_accounts PRIMARY KEY (enrollment_id),
    CONSTRAINT fk_fee_accounts_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES student_enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fee_accounts_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fee_accounts_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT ck_fee_accounts_registration_fee CHECK (registration_fee IS NULL OR registration_fee >= 0)
);

CREATE INDEX ix_fee_accounts_year ON fee_accounts (academic_year_id);

-- Dues of earlier years: one amount per student. "Clear" stores 0 and no note; the row is never deleted.
CREATE TABLE fee_brought_forward (
    student_id   BIGINT        NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    note         TEXT,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_fee_brought_forward PRIMARY KEY (student_id),
    CONSTRAINT fk_fee_brought_forward_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT ck_fee_brought_forward_amount CHECK (amount >= 0),
    CONSTRAINT ck_fee_brought_forward_note CHECK (note IS NULL OR char_length(note) <= 200)
);
