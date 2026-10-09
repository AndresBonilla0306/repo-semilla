package co.conciliacion.backend.application.port.out;

import co.conciliacion.backend.domain.model.Payment;

public interface PaymentRepository {

    boolean existsByReference(String reference);

    void save(Payment payment);
}