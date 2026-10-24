-- Student categories and their seed (FR-018, FR-019)
-- affects_fees is false for every seeded category until the school confirms the real values.

CREATE TABLE student_categories (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    code          TEXT    NOT NULL,
    name          TEXT    NOT NULL,
    affects_fees  BOOLEAN NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT true,
    version       BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_student_categories PRIMARY KEY (id),
    CONSTRAINT uq_student_categories_code UNIQUE (code)
);

INSERT INTO student_categories (code, name, affects_fees) VALUES
    ('O/S',     'O/S',     false),
    ('CEW',     'CEW',     false),
    ('W',       'W',       false),
    ('STAFF',   'STAFF',   false),
    ('S STAFF', 'S STAFF', false),
    ('RTE',     'RTE',     false),
    ('CASUAL',  'CASUAL',  false);
