package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.common.InvoiceFilter;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.in.SearchInvoicesUseCase;
import co.conciliacion.backend.application.port.out.InvoiceQueryPort;
import co.conciliacion.backend.domain.model.Invoice;

public class SearchInvoicesService implements SearchInvoicesUseCase {

    private static final int MAX_SIZE = 100;
    private final InvoiceQueryPort query;

    public SearchInvoicesService(InvoiceQueryPort query) {
        this.query = query;
    }

    @Override
    public PageResult<Invoice> search(InvoiceFilter f, int page, int size) {
        if (f.dueFrom() != null && f.dueTo() != null && f.dueFrom().isAfter(f.dueTo())) {
            throw new IllegalArgumentException("dueFrom no puede ser posterior a dueTo");
        }
        if (f.balanceMin() != null && f.balanceMax() != null && f.balanceMin().isGreaterThan(f.balanceMax())) {
            throw new IllegalArgumentException("balanceMin no puede ser mayor que balanceMax");
        }
        String nit = (f.nit() == null || f.nit().isBlank()) ? null : f.nit().strip();
        InvoiceFilter clean = new InvoiceFilter(nit, f.status(), f.dueFrom(), f.dueTo(),
            f.balanceMin(), f.balanceMax());
        return query.search(clean, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
    }
}