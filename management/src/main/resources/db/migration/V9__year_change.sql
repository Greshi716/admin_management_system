-- Year Change, Promotion runs, Carry-forward, and Google Form admissions (F4: FR-016, FR-017, FR-025, FR-030, FR-031, FR-035)
-- Every foreign key is ON DELETE RESTRICT; promotion tables are append-only.

-- 1. Additive columns on existing tables
ALTER TABLE student_enrollments ADD COLUMN stream TEXT NULL;
ALTER TABLE student_enrollments ADD CONSTRAINT ck_student_enrollments_stream
    CHECK (stream IS NULL OR stream IN ('Science', 'Commerce'));

ALTER TABLE standards ADD COLUMN uses_stream BOOLEAN NOT NULL DEFAULT false;

-- 2. Promotion runs (append-only)
CREATE TABLE promotion_runs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    from_year_id     BIGINT        NOT NULL,
    to_year_id       BIGINT        NOT NULL,
    standard_id      BIGINT        NOT NULL,
    done_by          BIGINT        NOT NULL,
    done_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    promoted_count   INT           NOT NULL DEFAULT 0,
    detained_count   INT           NOT NULL DEFAULT 0,
    left_count       INT           NOT NULL DEFAULT 0,
    carried_total    NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT pk_promotion_runs PRIMARY KEY (id),
    CONSTRAINT fk_promotion_runs_from_year FOREIGN KEY (from_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_runs_to_year FOREIGN KEY (to_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_runs_standard FOREIGN KEY (standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_runs_done_by FOREIGN KEY (done_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_promotion_runs_years_standard ON promotion_runs (from_year_id, to_year_id, standard_id);

-- 3. Promotion run items (append-only)
CREATE TABLE promotion_run_items (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    run_id                  BIGINT        NOT NULL,
    student_id              BIGINT        NOT NULL,
    from_enrollment_id      BIGINT        NOT NULL,
    to_enrollment_id        BIGINT,
    outcome                 TEXT          NOT NULL,
    to_standard_id          BIGINT,
    to_division_id          BIGINT,
    to_category_id          BIGINT,
    carried_amount          NUMERIC(12,2) NOT NULL DEFAULT 0,
    brought_forward_before  NUMERIC(12,2) NOT NULL DEFAULT 0,
    brought_forward_after   NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT pk_promotion_run_items PRIMARY KEY (id),
    CONSTRAINT uq_promotion_run_items_from_enrollment UNIQUE (from_enrollment_id),
    CONSTRAINT fk_promotion_run_items_run FOREIGN KEY (run_id)
        REFERENCES promotion_runs (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_from_enrollment FOREIGN KEY (from_enrollment_id)
        REFERENCES student_enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_to_enrollment FOREIGN KEY (to_enrollment_id)
        REFERENCES student_enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_to_standard FOREIGN KEY (to_standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_to_division FOREIGN KEY (to_division_id)
        REFERENCES divisions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_promotion_run_items_to_category FOREIGN KEY (to_category_id)
        REFERENCES student_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_promotion_run_items_outcome
        CHECK (outcome IN ('PROMOTED', 'DETAINED', 'LEFT'))
);

CREATE INDEX ix_promotion_run_items_run ON promotion_run_items (run_id);
CREATE INDEX ix_promotion_run_items_student ON promotion_run_items (student_id);

-- 4. Promotion corrections (append-only, written by developer correction)
CREATE TABLE promotion_corrections (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    item_id          BIGINT        NOT NULL,
    reason           TEXT          NOT NULL,
    done_by          TEXT          NOT NULL,
    done_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    amount_reversed  NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT pk_promotion_corrections PRIMARY KEY (id),
    CONSTRAINT uq_promotion_corrections_item UNIQUE (item_id),
    CONSTRAINT fk_promotion_corrections_item FOREIGN KEY (item_id)
        REFERENCES promotion_run_items (id) ON DELETE RESTRICT,
    CONSTRAINT ck_promotion_corrections_reason CHECK (char_length(btrim(reason)) > 0),
    CONSTRAINT ck_promotion_corrections_done_by CHECK (char_length(btrim(done_by)) > 0)
);

-- 5. Admission import batches
CREATE TABLE admission_import_batches (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    file_name     TEXT        NOT NULL,
    uploaded_by   BIGINT      NOT NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    rows_total    INT         NOT NULL DEFAULT 0,
    rows_added    INT         NOT NULL DEFAULT 0,
    rows_skipped  INT         NOT NULL DEFAULT 0,
    rows_invalid  INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_admission_import_batches PRIMARY KEY (id),
    CONSTRAINT fk_admission_import_batches_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES users (id) ON DELETE RESTRICT
);

-- 6. Admission requests
CREATE TABLE admission_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    submitted_at      TIMESTAMPTZ,
    form_data         JSONB       NOT NULL,
    import_batch_id   BIGINT      NOT NULL,
    import_key        TEXT        NOT NULL,
    duplicate_flag    BOOLEAN     NOT NULL DEFAULT false,
    duplicate_of      JSONB,
    status            TEXT        NOT NULL DEFAULT 'Pending',
    reviewed_by       BIGINT,
    reviewed_at       TIMESTAMPTZ,
    remarks           TEXT,
    student_id        BIGINT,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_admission_requests PRIMARY KEY (id),
    CONSTRAINT uq_admission_requests_import_key UNIQUE (import_key),
    CONSTRAINT fk_admission_requests_batch FOREIGN KEY (import_batch_id)
        REFERENCES admission_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_requests_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admission_requests_student FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT ck_admission_requests_status
        CHECK (status IN ('Pending', 'Approved', 'Rejected')),
    CONSTRAINT ck_admission_requests_approved
        CHECK (status <> 'Approved' OR (student_id IS NOT NULL AND reviewed_by IS NOT NULL)),
    CONSTRAINT ck_admission_requests_rejected
        CHECK (status <> 'Rejected' OR (remarks IS NOT NULL AND char_length(btrim(remarks)) > 0))
);

CREATE INDEX ix_admission_requests_status ON admission_requests (status);
CREATE INDEX ix_admission_requests_batch ON admission_requests (import_batch_id);

-- 7. Append-only triggers for promotion tables
CREATE FUNCTION promotions_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_promotion_runs_append_only BEFORE UPDATE OR DELETE ON promotion_runs
    FOR EACH ROW EXECUTE FUNCTION promotions_reject_change();
CREATE TRIGGER trg_promotion_runs_no_truncate BEFORE TRUNCATE ON promotion_runs
    FOR EACH STATEMENT EXECUTE FUNCTION promotions_reject_change();

CREATE TRIGGER trg_promotion_run_items_append_only BEFORE UPDATE OR DELETE ON promotion_run_items
    FOR EACH ROW EXECUTE FUNCTION promotions_reject_change();
CREATE TRIGGER trg_promotion_run_items_no_truncate BEFORE TRUNCATE ON promotion_run_items
    FOR EACH STATEMENT EXECUTE FUNCTION promotions_reject_change();

CREATE TRIGGER trg_promotion_corrections_append_only BEFORE UPDATE OR DELETE ON promotion_corrections
    FOR EACH ROW EXECUTE FUNCTION promotions_reject_change();
CREATE TRIGGER trg_promotion_corrections_no_truncate BEFORE TRUNCATE ON promotion_corrections
    FOR EACH STATEMENT EXECUTE FUNCTION promotions_reject_change();

-- 8. Immutability trigger for finalized admission requests (FR-035)
CREATE FUNCTION admission_requests_reject_final_change() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status IN ('Approved', 'Rejected') THEN
            RAISE EXCEPTION 'admission request % is % and cannot be deleted', OLD.id, OLD.status;
        END IF;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status IN ('Approved', 'Rejected') THEN
            RAISE EXCEPTION 'admission request % is already % and cannot be changed', OLD.id, OLD.status;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admission_requests_final_change BEFORE UPDATE OR DELETE ON admission_requests
    FOR EACH ROW EXECUTE FUNCTION admission_requests_reject_final_change();
