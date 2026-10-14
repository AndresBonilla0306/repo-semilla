package co.conciliacion.backend.application.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record Dashboard(BigDecimal totalInvoiced, BigDecimal totalApplied, BigDecimal totalRejected,
                        List<SupplierBalance> topSuppliers) {
    public record SupplierBalance(String nit, String name, BigDecimal pendingBalance) { }
}