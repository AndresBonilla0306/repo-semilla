package co.conciliacion.backend.application.port.out;

import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;

public interface InvoiceQueryPort {
    PageResult<Invoice> search(String nit, InvoiceStatus status, int page, int size);
}