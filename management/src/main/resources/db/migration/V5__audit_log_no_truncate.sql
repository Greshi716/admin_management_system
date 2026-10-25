-- V2 blocks UPDATE and DELETE on audit_log row by row. TRUNCATE skips row triggers, so it needs its own
-- statement-level trigger (FR-024). Same function, same error text.
CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
