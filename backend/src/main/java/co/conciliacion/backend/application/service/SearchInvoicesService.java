package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.in.SearchInvoicesUseCase;
import co.conciliacion.backend.application.port.out.InvoiceQueryPort;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;

public class SearchInvoicesService implements SearchInvoicesUseCase {

    private static final int MAX_SIZE = 100;
    private final InvoiceQueryPort query;

    public SearchInvoicesService(InvoiceQueryPort query) {
        this.query = query;
    }

    @Override
    public PageResult<Invoice> search(String nit, InvoiceStatus status, int page, int size) {
        String cleanNit = (nit == null || nit.isBlank()) ? null : nit.strip();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return query.search(cleanNit, status, safePage, safeSize);
    }
}