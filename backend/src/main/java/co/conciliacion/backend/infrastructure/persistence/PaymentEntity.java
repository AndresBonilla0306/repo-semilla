package co.conciliacion.backend.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "reference", length = 50)
    private String reference;

    @Column(name = "invoice_number", nullable = false, length = 20)
    private String invoiceNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    protected PaymentEntity() { }

    public PaymentEntity(String reference, String invoiceNumber, BigDecimal amount, OffsetDateTime paidAt) {
        this.reference = reference;
        this.invoiceNumber = invoiceNumber;
        this.amount = amount;
        this.paidAt = paidAt;
    }
}