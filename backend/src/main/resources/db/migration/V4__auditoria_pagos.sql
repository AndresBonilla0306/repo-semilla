CREATE TABLE payment_audit (
    id                BIGSERIAL     PRIMARY KEY,
    payment_reference VARCHAR(50)   NOT NULL,
    invoice_number    VARCHAR(20)   NOT NULL,
    amount            NUMERIC(19,2) NOT NULL,
    paid_at           TIMESTAMPTZ   NOT NULL,
    outcome           VARCHAR(10)   NOT NULL,
    reason            VARCHAR(40),
    detail            TEXT,
    balance_before    NUMERIC(19,2),
    balance_after     NUMERIC(19,2),
    performed_by      VARCHAR(100)  NOT NULL,
    occurred_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('APPLIED', 'REJECTED')),
    CONSTRAINT ck_audit_coherence CHECK (
        (outcome = 'APPLIED' AND reason IS NULL
            AND balance_before IS NOT NULL AND balance_after IS NOT NULL
            AND balance_after = balance_before - amount)
        OR (outcome = 'REJECTED' AND reason IS NOT NULL))
);

CREATE INDEX ix_audit_invoice ON payment_audit (invoice_number, occurred_at);
CREATE INDEX ix_audit_reference ON payment_audit (payment_reference);