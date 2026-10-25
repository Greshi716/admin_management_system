-- Users and the append-only audit log (FR-024, FR-025, FR-044)

CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    name              TEXT        NOT NULL,
    username          TEXT        NOT NULL,
    password_hash     TEXT        NOT NULL,
    role              TEXT        NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT true,
    last_login        TIMESTAMPTZ,
    password_changed  BOOLEAN     NOT NULL DEFAULT false,
    failed_attempts   INT         NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role = 'ADMIN')
);

CREATE TABLE audit_log (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id             BIGINT,
    username_attempted  TEXT,
    area                TEXT        NOT NULL,
    action              TEXT        NOT NULL,
    entity              TEXT        NOT NULL,
    changes             JSONB,
    reason              TEXT,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_audit_log_action CHECK (action IN
        ('Create', 'Edit', 'Delete', 'Login', 'Export', 'Cancel', 'Finalise', 'Import'))
);

CREATE INDEX ix_audit_log_at ON audit_log (at);
CREATE INDEX ix_audit_log_area_action_at ON audit_log (area, action, at);

CREATE FUNCTION audit_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();
