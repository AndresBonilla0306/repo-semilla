package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.out.AuditRepository;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.application.port.out.PaymentRepository;
import co.conciliacion.backend.domain.model.AuditEntry;
import co.conciliacion.backend.domain.model.AuditOutcome;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.Money;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import co.conciliacion.backend.domain.model.RejectionReason;
import java.time.Clock;
import java.util.Optional;

/** Sin anotaciones de Spring: la transaccion y el cableado se resuelven en infrastructure. */
public class ApplyPaymentService implements ApplyPaymentUseCase {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final AuditRepository audit;
    private final Clock clock;
    private final String actor;

    public ApplyPaymentService(InvoiceRepository invoices, PaymentRepository payments,
                               AuditRepository audit, Clock clock, String actor) {
        this.invoices = invoices;
        this.payments = payments;
        this.audit = audit;
        this.clock = clock;
        this.actor = actor;
    }

    @Override
    public PaymentResult apply(Payment payment) {
        Optional<Invoice> found = invoices.findByNumberForUpdate(payment.invoiceNumber());
        if (found.isEmpty()) {
            return reject(payment, RejectionReason.INVOICE_NOT_FOUND,
                "No existe la factura " + payment.invoiceNumber(), null);
        }
        Invoice invoice = found.get();
        if (payments.existsByReference(payment.reference())) {
            return reject(payment, RejectionReason.DUPLICATE_REFERENCE,
                "La referencia " + payment.reference() + " ya fue aplicada", invoice.balance());
        }
        if (!invoice.canReceive(payment.amount())) {
            return reject(payment, RejectionReason.AMOUNT_EXCEEDS_BALANCE,
                "El pago " + payment.amount() + " excede el saldo pendiente " + invoice.balance(),
                invoice.balance());
        }
        Invoice updated = invoice.applyPayment(payment.amount(), payment.paidAt());
        invoices.save(updated);
        payments.save(payment);
        audit.record(new AuditEntry(payment.reference(), payment.invoiceNumber(), payment.amount(),
            payment.paidAt(), AuditOutcome.APPLIED, null, null,
            invoice.balance(), updated.balance(), actor, clock.instant()));
        return new PaymentResult.Applied(updated, invoice.balance());
    }

    private PaymentResult reject(Payment payment, RejectionReason reason, String detail, Money balance) {
        audit.record(new AuditEntry(payment.reference(), payment.invoiceNumber(), payment.amount(),
            payment.paidAt(), AuditOutcome.REJECTED, reason, detail,
            balance, balance, actor, clock.instant()));
        return new PaymentResult.Rejected(reason, detail);
    }

    
}