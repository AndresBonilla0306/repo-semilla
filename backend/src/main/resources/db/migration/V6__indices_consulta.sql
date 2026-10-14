CREATE INDEX ix_invoice_due ON invoice (due_date, number);
CREATE INDEX ix_invoice_balance ON invoice (balance, number);
CREATE INDEX ix_invoice_status_due ON invoice (status, due_date);
ANALYZE invoice;