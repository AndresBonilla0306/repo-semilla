package co.conciliacion.backend.application.port.out;

import co.conciliacion.backend.application.dashboard.Dashboard;
import java.time.LocalDate;

public interface DashboardQueryPort {
    Dashboard query(LocalDate dueFrom, LocalDate dueTo);
}