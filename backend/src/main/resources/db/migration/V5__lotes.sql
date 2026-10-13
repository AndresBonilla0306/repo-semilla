CREATE TABLE batch (
    id             VARCHAR(100) PRIMARY KEY,
    origin         VARCHAR(50)  NOT NULL,
    generated_at   DATE,
    content_hash   CHAR(64)     NOT NULL,
    status         VARCHAR(12)  NOT NULL,
    total_lines    INT          NOT NULL DEFAULT 0,
    applied_lines  INT          NOT NULL DEFAULT 0,
    rejected_lines INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_batch_status CHECK (status IN ('PROCESSING', 'DONE'))
);

CREATE TABLE batch_line (
    batch_id       VARCHAR(100) NOT NULL REFERENCES batch(id),
    line_number    INT          NOT NULL,
    reference      TEXT,
    invoice_number TEXT,
    amount         TEXT,
    outcome        VARCHAR(10)  NOT NULL,
    reason         VARCHAR(40),
    detail         TEXT,
    PRIMARY KEY (batch_id, line_number)
);

CREATE INDEX ix_batch_line_reason ON batch_line (batch_id, reason);