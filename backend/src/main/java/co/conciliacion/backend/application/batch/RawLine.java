package co.conciliacion.backend.application.batch;

public record RawLine(int number, String reference, String invoiceNumber,
                      String amount, String paidAt, String formatError) { }