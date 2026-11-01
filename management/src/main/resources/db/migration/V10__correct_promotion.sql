-- Migration V10: Developer correction function for promotion runs (FR-025)
-- Run only by developer via psql in accordance with docs/runbooks/correct-promotion.md.
-- Never called by the application. Never deletes records.

CREATE OR REPLACE FUNCTION correct_promotion(
    p_item_id             BIGINT,
    p_reason              TEXT,
    p_done_by             TEXT,
    p_to_standard_id      BIGINT  DEFAULT NULL,
    p_to_division_id      BIGINT  DEFAULT NULL,
    p_to_category_id      BIGINT  DEFAULT NULL,
    p_reactivate_student  BOOLEAN DEFAULT FALSE
) RETURNS VOID AS $$
DECLARE
    item               promotion_run_items%ROWTYPE;
    v_prev_receipts    NUMERIC(12,2) := 0;
    v_current_bf       NUMERIC(12,2) := 0;
    v_target_year_id   BIGINT;
    v_new_fee          NUMERIC(12,2);
BEGIN
    IF btrim(coalesce(p_reason, '')) = '' THEN
        RAISE EXCEPTION 'correct_promotion: a reason is required';
    END IF;
    IF char_length(btrim(p_reason)) > 200 THEN
        RAISE EXCEPTION 'correct_promotion: the reason is at most 200 characters';
    END IF;
    IF btrim(coalesce(p_done_by, '')) = '' THEN
        RAISE EXCEPTION 'correct_promotion: the name of the person who does it is required';
    END IF;

    SELECT * INTO item FROM promotion_run_items WHERE id = p_item_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'correct_promotion: no promotion run item with id %', p_item_id;
    END IF;

    IF EXISTS (SELECT 1 FROM promotion_corrections WHERE item_id = p_item_id) THEN
        RAISE EXCEPTION 'correct_promotion: promotion run item % is already corrected', p_item_id;
    END IF;

    -- 1. Reverse brought-forward carried amount if > 0, respecting the previous-years receipts floor
    IF item.carried_amount > 0 THEN
        SELECT coalesce(sum(r.fee_paid), 0) INTO v_prev_receipts
        FROM receipts r
        WHERE r.student_id = item.student_id
          AND r.receipt_type = 'PREVIOUS_YEARS'
          AND NOT EXISTS (
              SELECT 1 FROM receipt_marks m WHERE m.receipt_serial_no = r.serial_no
          );

        SELECT coalesce(amount, 0) INTO v_current_bf
        FROM fee_brought_forward
        WHERE student_id = item.student_id;

        IF (v_current_bf - item.carried_amount) < v_prev_receipts THEN
            RAISE EXCEPTION 'correct_promotion: reduced brought forward amount % would be below valid previous-years receipts %',
                (v_current_bf - item.carried_amount), v_prev_receipts;
        END IF;

        UPDATE fee_brought_forward
        SET amount = amount - item.carried_amount,
            version = version + 1,
            updated_at = now()
        WHERE student_id = item.student_id;
    END IF;

    -- 2. Put old record back to 'Studying'
    UPDATE student_enrollments
    SET year_status = 'Studying',
        version = version + 1
    WHERE id = item.from_enrollment_id;

    -- 3. If new record exists and new standard given, repoint it and recalculate fee account
    IF item.to_enrollment_id IS NOT NULL AND p_to_standard_id IS NOT NULL THEN
        SELECT academic_year_id INTO v_target_year_id
        FROM student_enrollments
        WHERE id = item.to_enrollment_id;

        UPDATE student_enrollments
        SET standard_id = p_to_standard_id,
            division_id = coalesce(p_to_division_id, division_id),
            category_id = coalesce(p_to_category_id, category_id),
            version = version + 1
        WHERE id = item.to_enrollment_id;

        SELECT r.amount INTO v_new_fee
        FROM fee_rows r
        WHERE r.academic_year_id = v_target_year_id
          AND r.standard_id = p_to_standard_id
          AND (r.category_id = coalesce(p_to_category_id, item.to_category_id) OR r.category_id IS NULL)
          AND r.amount IS NOT NULL
        ORDER BY r.category_id NULLS LAST
        LIMIT 1;

        IF v_new_fee IS NOT NULL THEN
            UPDATE fee_accounts
            SET amount = v_new_fee,
                updated_at = now()
            WHERE enrollment_id = item.to_enrollment_id;
        END IF;
    END IF;

    -- 4. Left students: return student status to Active only if p_reactivate_student is true
    IF item.outcome = 'LEFT' AND p_reactivate_student THEN
        UPDATE students
        SET status = 'Active',
            version = version + 1
        WHERE id = item.student_id;
    END IF;

    -- 5. Record correction (append-only)
    INSERT INTO promotion_corrections (item_id, reason, done_by, done_at, amount_reversed)
    VALUES (p_item_id, btrim(p_reason), btrim(p_done_by), now(), item.carried_amount);

    -- 6. Insert audit log
    INSERT INTO audit_log (at, user_id, area, action, entity, changes, reason)
    VALUES (now(), NULL, 'Students', 'Edit', 'Student#' || item.student_id,
            jsonb_build_object('item_id', p_item_id,
                               'outcome', item.outcome,
                               'amountReversed', to_jsonb(item.carried_amount),
                               'by', btrim(p_done_by)),
            btrim(p_reason));
END;
$$ LANGUAGE plpgsql;
