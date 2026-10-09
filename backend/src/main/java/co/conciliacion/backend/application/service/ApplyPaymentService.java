package co.conciliacion.backend.application.service;


import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import co.conciliacion.backend.domain.model.RejectionReason;
import java.util.Optional;

/** Sin anotaciones de Spring: la transaccion y el cableado se resuelven en infrastructure. */
public class ApplyPaymentService implements ApplyPaymentUseCase {

    private final InvoiceRepository invoices;

    public ApplyPaymentService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Override
    public PaymentResult apply(Payment payment) {
        Optional<Invoice> found = invoices.findByNumberForUpdate(payment.invoiceNumber());
        if (found.isEmpty()) {
            return new PaymentResult.Rejected(RejectionReason.INVOICE_NOT_FOUND,
                "No existe la factura " + payment.invoiceNumber());
        }
        Invoice invoice = found.get();
        if (!invoice.canReceive(payment.amount())) {
            // RF-09 y RF-11: se rechaza completo y no se guarda nada.
            return new PaymentResult.Rejected(RejectionReason.AMOUNT_EXCEEDS_BALANCE,
                "El pago " + payment.amount() + " excede el saldo pendiente " + invoice.balance());
        }
        Invoice updated = invoice.applyPayment(payment.amount());
        invoices.save(updated);
        return new PaymentResult.Applied(updated, invoice.balance());
    }
}