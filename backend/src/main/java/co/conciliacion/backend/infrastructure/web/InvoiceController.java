package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.common.InvoiceFilter;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.in.SearchInvoicesUseCase;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;
import co.conciliacion.backend.domain.model.Money;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) String balanceMin,
            @RequestParam(required = false) String balanceMax,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        InvoiceFilter filter = new InvoiceFilter(nit, status, dueFrom, dueTo,
            money(balanceMin), money(balanceMax));
        PageResult<Invoice> result = useCase.search(filter, page, size);
        return new PageResponse<>(result.items().stream().map(InvoiceResponse::from).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    private static Money money(String value) {
        return (value == null || value.isBlank()) ? null : Money.of(value.strip());
    }
}