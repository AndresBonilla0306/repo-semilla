package co.conciliacion.backend.application.port.in;

import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;

public interface SearchInvoicesUseCase {
    PageResult<Invoice> search(String nit, InvoiceStatus status, int page, int size);
}