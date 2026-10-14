package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.dashboard.Dashboard;
import co.conciliacion.backend.application.port.in.DashboardUseCase;
import co.conciliacion.backend.application.port.out.DashboardQueryPort;
import java.time.LocalDate;

public class DashboardService implements DashboardUseCase {

    private final DashboardQueryPort query;

    public DashboardService(DashboardQueryPort query) {
        this.query = query;
    }

    @Override
    public Dashboard get(LocalDate dueFrom, LocalDate dueTo) {
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new IllegalArgumentException("dueFrom no puede ser posterior a dueTo");
        }
        return query.query(dueFrom, dueTo);
    }
}