-- DEFAULT 'PENDING' mantiene valido el script seed/load-facturas.sql, que no informa el estado.
ALTER TABLE invoice ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE invoice
SET status = CASE
    WHEN balance = 0 THEN 'PAID'
    WHEN balance = total THEN 'PENDING'
    ELSE 'PARTIAL'
END
WHERE balance <> total;

ALTER TABLE invoice ADD CONSTRAINT ck_invoice_status
    CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'PAID_LATE'));

CREATE INDEX ix_invoice_status ON invoice (status, number);