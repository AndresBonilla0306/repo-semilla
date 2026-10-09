package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.port.out.PaymentRepository;
import co.conciliacion.backend.domain.model.Payment;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPaymentRepository implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    public JpaPaymentRepository(PaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByReference(String reference) {
        return jpa.existsById(reference);
    }

    @Override
    public void save(Payment payment) {
        jpa.save(new PaymentEntity(payment.reference(), payment.invoiceNumber(),
            payment.amount().amount(), payment.paidAt()));
    }
}