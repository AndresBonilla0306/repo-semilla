CREATE TABLE invoice (
    number        VARCHAR(20)   PRIMARY KEY,
    supplier_nit  VARCHAR(20)   NOT NULL,
    supplier_name VARCHAR(200)  NOT NULL,
    issue_date    DATE          NOT NULL,
    due_date      DATE          NOT NULL,
    total         NUMERIC(19,2) NOT NULL,
    balance       NUMERIC(19,2) NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoice_total   CHECK (total > 0),
    CONSTRAINT ck_invoice_balance CHECK (balance >= 0 AND balance <= total),
    CONSTRAINT ck_invoice_dates   CHECK (due_date >= issue_date)
);

CREATE TABLE payment (
    reference      VARCHAR(50)   PRIMARY KEY,
    invoice_number VARCHAR(20)   NOT NULL REFERENCES invoice(number),
    amount         NUMERIC(19,2) NOT NULL,
    paid_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);

CREATE INDEX ix_payment_invoice ON payment(invoice_number);