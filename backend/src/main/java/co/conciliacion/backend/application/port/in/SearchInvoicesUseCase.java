package co.conciliacion.backend.application.port.in;

import co.conciliacion.backend.application.common.InvoiceFilter;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.domain.model.Invoice;

public interface SearchInvoicesUseCase {
    PageResult<Invoice> search(InvoiceFilter filter, int page, int size);
}