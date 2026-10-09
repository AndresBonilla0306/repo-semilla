package co.conciliacion.backend.domain.model;

public enum RejectionReason {
    INVOICE_NOT_FOUND,
    AMOUNT_EXCEEDS_BALANCE,
    DUPLICATE_REFERENCE
}