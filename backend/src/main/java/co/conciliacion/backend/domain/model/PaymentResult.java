package co.conciliacion.backend.domain.model;


/** Resultado de intentar aplicar un pago: aplicado o rechazado con motivo. */
public sealed interface PaymentResult {

    record Applied(Invoice invoice, Money previousBalance) implements PaymentResult { }

    record Rejected(RejectionReason reason, String detail) implements PaymentResult { }
}
