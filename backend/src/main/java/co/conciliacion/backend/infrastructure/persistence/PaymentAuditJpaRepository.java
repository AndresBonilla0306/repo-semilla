package co.conciliacion.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAuditJpaRepository extends JpaRepository<PaymentAuditEntity, Long> { }