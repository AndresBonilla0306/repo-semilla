package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.port.out.AuditRepository;
import co.conciliacion.backend.domain.model.AuditEntry;
import co.conciliacion.backend.domain.model.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditRepository implements AuditRepository {

    private final PaymentAuditJpaRepository jpa;

    public JpaAuditRepository(PaymentAuditJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void record(AuditEntry e) {
        jpa.save(new PaymentAuditEntity(e.paymentReference(), e.invoiceNumber(),
            e.amount().amount(), e.paidAt(), e.outcome().name(),
            e.reason() == null ? null : e.reason().name(), e.detail(),
            value(e.balanceBefore()), value(e.balanceAfter()), e.actor(), e.occurredAt()));
    }

    private BigDecimal value(Money money) {
        return money == null ? null : money.amount();
    }
}