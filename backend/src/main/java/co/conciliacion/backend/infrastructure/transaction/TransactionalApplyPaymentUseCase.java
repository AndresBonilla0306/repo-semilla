package co.conciliacion.backend.infrastructure.transaction;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decorador: agrega la frontera transaccional sin tocar el caso de uso.
 * El bloqueo FOR UPDATE de findByNumberForUpdate dura hasta el commit de este método.
 */
@Transactional
public class TransactionalApplyPaymentUseCase implements ApplyPaymentUseCase {

    private final ApplyPaymentUseCase delegate;

    public TransactionalApplyPaymentUseCase(ApplyPaymentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    public PaymentResult apply(Payment payment) {
        return delegate.apply(payment);
    }
}