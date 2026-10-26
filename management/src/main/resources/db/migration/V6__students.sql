-- Students, GR numbers, yearly class records and photos (F1: FR-001, FR-002, FR-006, FR-017)
-- Nothing here is ever deleted by the application (constitution IV); every foreign key is ON DELETE RESTRICT.

CREATE SEQUENCE student_sr_no_seq;

CREATE TABLE students (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    gr_no                   TEXT        NOT NULL,
    sr_no                   BIGINT      NOT NULL DEFAULT nextval('student_sr_no_seq'),
    surname                 TEXT        NOT NULL,
    first_name              TEXT        NOT NULL,
    middle_name             TEXT,
    gender                  TEXT        NOT NULL,
    dob                     DATE        NOT NULL,
    birth_place             TEXT,
    father_name             TEXT,
    mother_name             TEXT,
    mobile_1                TEXT        NOT NULL,
    mobile_2                TEXT,
    address                 TEXT        NOT NULL,
    aadhaar                 TEXT,
    religion_caste          TEXT,
    category_id             BIGINT      NOT NULL,
    admission_date          DATE        NOT NULL,
    admission_standard_id   BIGINT      NOT NULL,
    previous_school         TEXT,
    photo_version           BIGINT,
    status                  TEXT        NOT NULL DEFAULT 'Active',
    source                  TEXT        NOT NULL DEFAULT 'Manual',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_students PRIMARY KEY (id),
    CONSTRAINT uq_students_sr_no UNIQUE (sr_no),
    CONSTRAINT fk_students_category FOREIGN KEY (category_id)
        REFERENCES student_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_students_admission_standard FOREIGN KEY (admission_standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT ck_students_gender CHECK (gender IN ('Male', 'Female', 'Other')),
    CONSTRAINT ck_students_mobile_1 CHECK (mobile_1 ~ '^[0-9]{10}$'),
    CONSTRAINT ck_students_mobile_2 CHECK (mobile_2 IS NULL OR mobile_2 ~ '^[0-9]{10}$'),
    CONSTRAINT ck_students_aadhaar CHECK (aadhaar IS NULL OR aadhaar ~ '^[0-9]{12}$'),
    CONSTRAINT ck_students_admission_after_dob CHECK (admission_date >= dob),
    CONSTRAINT ck_students_status CHECK (status IN ('Active', 'Left')),
    CONSTRAINT ck_students_source CHECK (source IN ('Manual', 'Google Form'))
);

CREATE UNIQUE INDEX uq_students_gr_no_lower ON students (lower(gr_no));
CREATE INDEX ix_students_name ON students (lower(surname), lower(first_name));
CREATE INDEX ix_students_mobile_1 ON students (mobile_1);
CREATE INDEX ix_students_mobile_2 ON students (mobile_2);
CREATE INDEX ix_students_status ON students (status);
CREATE INDEX ix_students_category ON students (category_id);
CREATE INDEX ix_students_admission_standard ON students (admission_standard_id);

-- Every GR No. ever given to a student, so a corrected number stays reserved.
CREATE TABLE student_gr_numbers (
    gr_key       TEXT        NOT NULL,
    student_id   BIGINT      NOT NULL,
    assigned_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_student_gr_numbers PRIMARY KEY (gr_key),
    CONSTRAINT fk_student_gr_numbers_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT
);

-- Yearly class record: one row per student per academic year.
CREATE TABLE student_enrollments (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    student_id        BIGINT NOT NULL,
    academic_year_id  BIGINT NOT NULL,
    standard_id       BIGINT NOT NULL,
    division_id       BIGINT NOT NULL,
    category_id       BIGINT NOT NULL,
    year_start_date   DATE   NOT NULL,
    year_status       TEXT   NOT NULL DEFAULT 'Studying',
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_student_enrollments PRIMARY KEY (id),
    CONSTRAINT uq_student_enrollments_student_year UNIQUE (student_id, academic_year_id),
    CONSTRAINT fk_student_enrollments_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_enrollments_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_enrollments_standard FOREIGN KEY (standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_enrollments_division FOREIGN KEY (division_id)
        REFERENCES divisions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_enrollments_category FOREIGN KEY (category_id)
        REFERENCES student_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_enrollments_year_status CHECK (year_status IN ('Studying', 'Promoted', 'Detained', 'Left'))
);

CREATE INDEX ix_student_enrollments_class ON student_enrollments (academic_year_id, standard_id, division_id);
CREATE INDEX ix_student_enrollments_category ON student_enrollments (academic_year_id, category_id);

CREATE TABLE student_photos (
    student_id    BIGINT      NOT NULL,
    content       BYTEA       NOT NULL,
    content_type  TEXT        NOT NULL,
    size_bytes    INT         NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_student_photos PRIMARY KEY (student_id),
    CONSTRAINT fk_student_photos_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT
);

-- The profile History block reads the audit log by record id without scanning.
CREATE INDEX ix_audit_log_entity_at ON audit_log (entity, at);
