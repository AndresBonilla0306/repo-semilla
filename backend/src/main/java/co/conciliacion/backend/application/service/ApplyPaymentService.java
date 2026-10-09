package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.application.port.out.PaymentRepository;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import co.conciliacion.backend.domain.model.RejectionReason;
import java.util.Optional;

/** Sin anotaciones de Spring: la transaccion y el cableado se resuelven en infrastructure. */
public class ApplyPaymentService implements ApplyPaymentUseCase {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public ApplyPaymentService(InvoiceRepository invoices, PaymentRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    @Override
    public PaymentResult apply(Payment payment) {
        // 1. Se bloquea la factura primero: serializa a quienes compiten por ella (CF-04, RF-12).
        Optional<Invoice> found = invoices.findByNumberForUpdate(payment.invoiceNumber());
        if (found.isEmpty()) {
            return new PaymentResult.Rejected(RejectionReason.INVOICE_NOT_FOUND,
                "No existe la factura " + payment.invoiceNumber());
        }
        // 2. Con el bloqueo tomado, el chequeo de referencia ya no tiene carreras sobre esta factura.
        if (payments.existsByReference(payment.reference())) {
            return new PaymentResult.Rejected(RejectionReason.DUPLICATE_REFERENCE,
                "La referencia " + payment.reference() + " ya fue aplicada");
        }
        Invoice invoice = found.get();
        if (!invoice.canReceive(payment.amount())) {
            return new PaymentResult.Rejected(RejectionReason.AMOUNT_EXCEEDS_BALANCE,
                "El pago " + payment.amount() + " excede el saldo pendiente " + invoice.balance());
        }
        Invoice updated = invoice.applyPayment(payment.amount());
        invoices.save(updated);
        payments.save(payment);
        return new PaymentResult.Applied(updated, invoice.balance());
    }
}