package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.in.SearchInvoicesUseCase;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/facturas")
public class InvoiceController {

    private final SearchInvoicesUseCase useCase;

    public InvoiceController(SearchInvoicesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public PageResponse<InvoiceResponse> search(
            @RequestParam(required = false) String nit,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResult<Invoice> result = useCase.search(nit, status, page, size);
        return new PageResponse<>(result.items().stream().map(InvoiceResponse::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}