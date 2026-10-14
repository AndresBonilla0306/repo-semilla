package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.dashboard.Dashboard;
import co.conciliacion.backend.application.port.in.DashboardUseCase;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conciliacion")
public class DashboardController {

    public record SupplierResponse(String nit, String name, String pendingBalance) { }

    public record DashboardResponse(String totalInvoiced, String totalApplied, String totalRejected,
                                    List<SupplierResponse> topSuppliers) { }

    private final DashboardUseCase useCase;

    public DashboardController(DashboardUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/tablero")
    public DashboardResponse get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
        Dashboard d = useCase.get(dueFrom, dueTo);
        return new DashboardResponse(d.totalInvoiced().toPlainString(), d.totalApplied().toPlainString(),
            d.totalRejected().toPlainString(),
            d.topSuppliers().stream().map(s -> new SupplierResponse(s.nit(), s.name(),
                s.pendingBalance().toPlainString())).toList());
    }
}