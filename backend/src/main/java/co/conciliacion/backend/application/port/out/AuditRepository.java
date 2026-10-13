package co.conciliacion.backend.application.port.out;

import co.conciliacion.backend.domain.model.AuditEntry;

public interface AuditRepository {
    void record(AuditEntry entry);
}