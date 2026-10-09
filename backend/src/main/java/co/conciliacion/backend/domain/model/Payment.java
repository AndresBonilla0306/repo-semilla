package co.conciliacion.backend.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Pago dirigido a una factura */
public record Payment(String reference, String invoiceNumber, Money amount, OffsetDateTime paidAt) {

    public Payment {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("La referencia es obligatoria");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("El numero de factura es obligatorio");
        }
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(paidAt, "paidAt");
        if (amount.isZero()) {
            throw new IllegalArgumentException("El valor del pago debe ser mayor que cero");
        }
        reference = reference.strip();
        invoiceNumber = invoiceNumber.strip();
    }
}
