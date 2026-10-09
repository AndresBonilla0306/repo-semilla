BEGIN;

CREATE TEMP TABLE stg_invoice (
    number text, nit text, name text,
    issue_date date, due_date date, total numeric(19,2)
) ON COMMIT DROP;

COPY stg_invoice FROM '/seed/facturas.csv'
    WITH (FORMAT csv, DELIMITER ';', HEADER true);

INSERT INTO invoice (number, supplier_nit, supplier_name, issue_date, due_date, total, balance)
SELECT number, nit, name, issue_date, due_date, total, total
FROM stg_invoice
ON CONFLICT (number) DO NOTHING;

COMMIT;