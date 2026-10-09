package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.domain.model.Invoice;
import java.time.LocalDate;

public record InvoiceResponse(String number, String supplierNit, String supplierName,
                              LocalDate issueDate, LocalDate dueDate,
                              String total, String balance, String status) {

    static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(i.number(), i.supplierNit(), i.supplierName(),
            i.issueDate(), i.dueDate(), i.total().toString(), i.balance().toString(),
            i.status().name());
    }
}