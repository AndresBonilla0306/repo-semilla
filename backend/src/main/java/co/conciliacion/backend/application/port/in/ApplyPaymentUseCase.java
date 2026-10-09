package co.conciliacion.backend.application.port.in;

import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;

public interface ApplyPaymentUseCase {
    PaymentResult apply(Payment payment);
}