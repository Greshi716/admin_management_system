-- Academic years, standards and divisions (FR-011, FR-015, FR-017, FR-020)

CREATE TABLE academic_years (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    name        TEXT    NOT NULL,
    start_date  DATE    NOT NULL,
    end_date    DATE    NOT NULL,
    version     BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_academic_years PRIMARY KEY (id),
    CONSTRAINT uq_academic_years_name UNIQUE (name),
    CONSTRAINT ck_academic_years_dates CHECK (end_date > start_date),
    CONSTRAINT ex_academic_years_no_overlap
        EXCLUDE USING gist (daterange(start_date, end_date, '[]') WITH &&)
);

CREATE TABLE standards (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    name           TEXT    NOT NULL,
    display_order  INT     NOT NULL,
    stream         TEXT,
    active         BOOLEAN NOT NULL DEFAULT true,
    version        BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_standards PRIMARY KEY (id),
    CONSTRAINT uq_standards_name UNIQUE (name),
    CONSTRAINT ck_standards_stream CHECK (stream IN ('Science', 'Commerce'))
);

CREATE TABLE divisions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    academic_year_id BIGINT  NOT NULL,
    standard_id      BIGINT  NOT NULL,
    name             TEXT    NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT true,
    version          BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_divisions PRIMARY KEY (id),
    CONSTRAINT fk_divisions_academic_year FOREIGN KEY (academic_year_id)
        REFERENCES academic_years (id) ON DELETE RESTRICT,
    CONSTRAINT fk_divisions_standard FOREIGN KEY (standard_id)
        REFERENCES standards (id) ON DELETE RESTRICT,
    CONSTRAINT uq_divisions_year_standard_name UNIQUE (academic_year_id, standard_id, name)
);
