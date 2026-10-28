-- Receipts, receipt marks, fee views and the developer void (F3: FR-001, FR-009, FR-012, FR-022, FR-023)
-- A receipt and a mark are never updated or deleted (constitution II); every foreign key is ON DELETE RESTRICT.

CREATE TABLE receipts (
    serial_no          BIGINT GENERATED ALWAYS AS IDENTITY,
    receipt_no         TEXT          NOT NULL,
    receipt_date       DATE          NOT NULL,
    academic_year_id   BIGINT        NOT NULL,
    student_id         BIGINT        NOT NULL,
    enrollment_id      BIGINT,
    student_name       TEXT          NOT NULL,
    gr_no              TEXT          NOT NULL,
    standard_id        BIGINT,
    division_id        BIGINT,
    category_id        BIGINT,
    standard_name      TEXT,
    division_name      TEXT,
    receipt_type       TEXT          NOT NULL,
    registration_paid  NUMERIC(12,2) NOT NULL DEFAULT 0,
    fee_paid           NUMERIC(12,2) NOT NULL,
    total              NUMERIC(12,2) NOT NULL,
    total_words        TEXT          NOT NULL,
    payment_mode       TEXT          NOT NULL,
    cheque_no          TEXT,
    bank_name          TEXT,
    cheque_date        DATE,
    created_by         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_receipts PRIMARY KEY (serial_no),
    CONSTRAINT fk_receipts_year FOREIGN KEY (academic_year_id) REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_enrollment FOREIGN KEY (enrollment_id) REFERENCES student_enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_standard FOREIGN KEY (standard_id) REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_division FOREIGN KEY (division_id) REFERENCES divisions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_category FOREIGN KEY (category_id) REFERENCES student_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_receipts_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_receipts_receipt_no CHECK (char_length(btrim(receipt_no)) BETWEEN 1 AND 20 AND receipt_no = btrim(receipt_no)),
    CONSTRAINT ck_receipts_type CHECK (receipt_type IN ('CURRENT_YEAR', 'PREVIOUS_YEARS')),
    CONSTRAINT ck_receipts_amounts CHECK (registration_paid >= 0 AND fee_paid >= 0),
    CONSTRAINT ck_receipts_total CHECK (total = registration_paid + fee_paid AND total > 0),
    CONSTRAINT ck_receipts_current CHECK (
        (receipt_type = 'CURRENT_YEAR' AND enrollment_id IS NOT NULL)
        OR (receipt_type = 'PREVIOUS_YEARS' AND enrollment_id IS NULL AND registration_paid = 0 AND fee_paid > 0)),
    CONSTRAINT ck_receipts_mode CHECK (
        (payment_mode = 'CASH' AND cheque_no IS NULL AND bank_name IS NULL AND cheque_date IS NULL)
        OR (payment_mode = 'CHEQUE' AND btrim(coalesce(cheque_no, '')) <> '' AND btrim(coalesce(bank_name, '')) <> ''
            AND cheque_date IS NOT NULL))
);

CREATE INDEX ix_receipts_receipt_no ON receipts (upper(receipt_no));
CREATE INDEX ix_receipts_student ON receipts (student_id, serial_no);
CREATE INDEX ix_receipts_enrollment ON receipts (enrollment_id) WHERE enrollment_id IS NOT NULL;
CREATE INDEX ix_receipts_previous ON receipts (student_id) WHERE receipt_type = 'PREVIOUS_YEARS';
CREATE INDEX ix_receipts_date ON receipts (receipt_date, serial_no);
CREATE INDEX ix_receipts_mode_date ON receipts (payment_mode, receipt_date);
CREATE INDEX ix_receipts_standard_date ON receipts (standard_id, receipt_date);

-- One row says a receipt no longer counts as paid. A receipt with no row is Valid.
CREATE TABLE receipt_marks (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY,
    receipt_serial_no  BIGINT        NOT NULL,
    kind               TEXT          NOT NULL,
    reason             TEXT          NOT NULL,
    amount_added_back  NUMERIC(12,2) NOT NULL,
    marked_by          BIGINT,
    marked_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_receipt_marks PRIMARY KEY (id),
    CONSTRAINT uq_receipt_marks_receipt UNIQUE (receipt_serial_no),
    CONSTRAINT fk_receipt_marks_receipt FOREIGN KEY (receipt_serial_no) REFERENCES receipts (serial_no) ON DELETE RESTRICT,
    CONSTRAINT fk_receipt_marks_user FOREIGN KEY (marked_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_receipt_marks_kind CHECK (kind IN ('CHEQUE_NOT_CLEARED', 'DEVELOPER_VOID')),
    CONSTRAINT ck_receipt_marks_reason CHECK (char_length(btrim(reason)) BETWEEN 1 AND 200),
    CONSTRAINT ck_receipt_marks_amount CHECK (amount_added_back > 0)
);

-- Append-only, as audit_log in V2 and V5: row triggers for UPDATE and DELETE, statement triggers for TRUNCATE.
CREATE FUNCTION receipts_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'receipts and receipt_marks are append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_receipts_append_only BEFORE UPDATE OR DELETE ON receipts
    FOR EACH ROW EXECUTE FUNCTION receipts_reject_change();
CREATE TRIGGER trg_receipts_no_truncate BEFORE TRUNCATE ON receipts
    FOR EACH STATEMENT EXECUTE FUNCTION receipts_reject_change();
CREATE TRIGGER trg_receipt_marks_append_only BEFORE UPDATE OR DELETE ON receipt_marks
    FOR EACH ROW EXECUTE FUNCTION receipts_reject_change();
CREATE TRIGGER trg_receipt_marks_no_truncate BEFORE TRUNCATE ON receipt_marks
    FOR EACH STATEMENT EXECUTE FUNCTION receipts_reject_change();

-- A receipt number is unique across all receipts except one voided by the developer correction (FR-001).
-- A trigger, not a unique index, because the voided state lives in another table. The advisory lock on the
-- number serialises two simultaneous saves of one number; the message carries the constraint name that
-- ApiExceptionHandler reads. A BEFORE trigger runs before the CHECKs, so it trims the number itself.
CREATE FUNCTION receipts_check_receipt_no() RETURNS trigger AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('receipt-no:' || upper(btrim(NEW.receipt_no)), 0));
    IF EXISTS (SELECT 1 FROM receipts r
               WHERE upper(r.receipt_no) = upper(btrim(NEW.receipt_no))
                 AND NOT EXISTS (SELECT 1 FROM receipt_marks m
                                 WHERE m.receipt_serial_no = r.serial_no AND m.kind = 'DEVELOPER_VOID')) THEN
        RAISE EXCEPTION 'duplicate key value violates unique constraint "uq_receipts_receipt_no"'
            USING ERRCODE = '23505', CONSTRAINT = 'uq_receipts_receipt_no';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_receipts_receipt_no BEFORE INSERT ON receipts
    FOR EACH ROW EXECUTE FUNCTION receipts_check_receipt_no();

-- A mark: Cheque not cleared only for a Cheque receipt; the amount added back is the receipt's total.
CREATE FUNCTION receipt_marks_check() RETURNS trigger AS $$
DECLARE
    r receipts%ROWTYPE;
BEGIN
    SELECT * INTO r FROM receipts WHERE serial_no = NEW.receipt_serial_no;
    IF NOT FOUND THEN
        RETURN NEW; -- the foreign key reports it
    END IF;
    IF NEW.kind = 'CHEQUE_NOT_CLEARED' AND r.payment_mode <> 'CHEQUE' THEN
        RAISE EXCEPTION 'only a Cheque receipt can be marked Cheque not cleared' USING ERRCODE = '23514';
    END IF;
    IF NEW.amount_added_back <> r.total THEN
        RAISE EXCEPTION 'the amount added back must equal the receipt total' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_receipt_marks_check BEFORE INSERT ON receipt_marks
    FOR EACH ROW EXECUTE FUNCTION receipt_marks_check();

-- Views owned by fees. v_valid_receipts is the one SQL definition of a valid receipt. The other two use lateral
-- sums so a lookup of one record reads only that record's receipts.
CREATE VIEW v_valid_receipts AS
SELECT r.* FROM receipts r
WHERE NOT EXISTS (SELECT 1 FROM receipt_marks m WHERE m.receipt_serial_no = r.serial_no);

CREATE VIEW v_fee_position AS
SELECT a.enrollment_id,
       a.academic_year_id,
       a.amount AS year_fee,
       a.registration_fee,
       x.paid_year_fee,
       x.paid_registration,
       CASE WHEN a.amount IS NULL THEN NULL ELSE GREATEST(a.amount - x.paid_year_fee, 0) END AS pending_year_fee,
       CASE WHEN a.registration_fee IS NULL THEN 0
            ELSE GREATEST(a.registration_fee - x.paid_registration, 0) END AS pending_registration,
       GREATEST(x.paid_year_fee - COALESCE(a.amount, 0), 0)
           + GREATEST(x.paid_registration - COALESCE(a.registration_fee, 0), 0) AS excess_paid
FROM fee_accounts a
CROSS JOIN LATERAL (
    SELECT COALESCE(SUM(r.fee_paid), 0) AS paid_year_fee, COALESCE(SUM(r.registration_paid), 0) AS paid_registration
    FROM v_valid_receipts r
    WHERE r.enrollment_id = a.enrollment_id AND r.receipt_type = 'CURRENT_YEAR') x;

CREATE VIEW v_earlier_pending AS
SELECT b.student_id, b.amount, x.paid, b.amount - x.paid AS pending
FROM fee_brought_forward b
CROSS JOIN LATERAL (
    SELECT COALESCE(SUM(r.fee_paid), 0) AS paid
    FROM v_valid_receipts r
    WHERE r.student_id = b.student_id AND r.receipt_type = 'PREVIOUS_YEARS') x;

-- Developer only (docs/runbooks/correct-receipt.md); the application never calls it. One transaction: a
-- DEVELOPER_VOID mark and one audit row (area Fees, action Edit) that names who did it.
CREATE FUNCTION void_receipt(p_serial_no BIGINT, p_reason TEXT, p_done_by TEXT) RETURNS VOID AS $$
DECLARE
    r receipts%ROWTYPE;
BEGIN
    IF btrim(coalesce(p_reason, '')) = '' THEN
        RAISE EXCEPTION 'void_receipt: a reason is required';
    END IF;
    IF char_length(btrim(p_reason)) > 200 THEN
        RAISE EXCEPTION 'void_receipt: the reason is at most 200 characters';
    END IF;
    IF btrim(coalesce(p_done_by, '')) = '' THEN
        RAISE EXCEPTION 'void_receipt: the name of the person who does it is required';
    END IF;
    SELECT * INTO r FROM receipts WHERE serial_no = p_serial_no;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'void_receipt: no receipt with serial no. %', p_serial_no;
    END IF;
    IF EXISTS (SELECT 1 FROM receipt_marks WHERE receipt_serial_no = p_serial_no) THEN
        RAISE EXCEPTION 'void_receipt: receipt % is already marked', p_serial_no;
    END IF;
    INSERT INTO receipt_marks (receipt_serial_no, kind, reason, amount_added_back, marked_by, marked_at)
    VALUES (p_serial_no, 'DEVELOPER_VOID', btrim(p_reason), r.total, NULL, now());
    INSERT INTO audit_log (at, user_id, area, action, entity, changes, reason)
    VALUES (now(), NULL, 'Fees', 'Edit', 'Receipt#' || p_serial_no,
            jsonb_build_object('old', jsonb_build_object('status', 'VALID'),
                               'new', jsonb_build_object('status', 'VOIDED'),
                               'amountAddedBack', to_jsonb(r.total), 'by', btrim(p_done_by)),
            btrim(p_reason));
END;
$$ LANGUAGE plpgsql;
