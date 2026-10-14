package co.conciliacion.backend.application.port.in;

import co.conciliacion.backend.application.dashboard.Dashboard;
import java.time.LocalDate;

public interface DashboardUseCase {
    Dashboard get(LocalDate dueFrom, LocalDate dueTo);
}