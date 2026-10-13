package co.conciliacion.backend.application.batch;

public record LineResult(int lineNumber, String reference, String invoiceNumber, String amount,
                         String outcome, String reason, String detail) { }