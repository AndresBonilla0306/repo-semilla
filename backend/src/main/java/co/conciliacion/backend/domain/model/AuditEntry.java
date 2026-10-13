package co.conciliacion.backend.domain.model;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Registro de auditoria de un intento de pago (RF-15). Los saldos pueden ser null si la factura no existe. */
public record AuditEntry(
    String paymentReference,
    String invoiceNumber,
    Money amount,
    OffsetDateTime paidAt,
    AuditOutcome outcome,
    RejectionReason reason,
    String detail,
    Money balanceBefore,
    Money balanceAfter,
    String actor,
    Instant occurredAt) { }