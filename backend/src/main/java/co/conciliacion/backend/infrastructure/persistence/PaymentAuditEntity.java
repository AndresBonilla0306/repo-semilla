package co.conciliacion.backend.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_audit")
public class PaymentAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false, length = 50)
    private String paymentReference;

    @Column(name = "invoice_number", nullable = false, length = 20)
    private String invoiceNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "reason", length = 40)
    private String reason;

    @Column(name = "detail")
    private String detail;

    @Column(name = "balance_before", precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentAuditEntity() { }

    public PaymentAuditEntity(String paymentReference, String invoiceNumber, BigDecimal amount,
                              OffsetDateTime paidAt, String outcome, String reason, String detail,
                              BigDecimal balanceBefore, BigDecimal balanceAfter,
                              String performedBy, Instant occurredAt) {
        this.paymentReference = paymentReference;
        this.invoiceNumber = invoiceNumber;
        this.amount = amount;
        this.paidAt = paidAt;
        this.outcome = outcome;
        this.reason = reason;
        this.detail = detail;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.performedBy = performedBy;
        this.occurredAt = occurredAt;
    }
}