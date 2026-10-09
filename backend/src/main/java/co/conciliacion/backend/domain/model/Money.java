package co.conciliacion.backend.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Valor monetario en pesos colombianos, siempre con 2 decimales exactos y nunca negativo. */
public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount");
        try {
            // UNNECESSARY: si llegan mas de 2 decimales significativos, falla en vez de redondear.
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("El valor admite maximo 2 decimales: " + amount, e);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El valor no puede ser negativo: " + amount);
        }
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        if (other.amount.compareTo(amount) > 0) {
            throw new IllegalArgumentException("Resultado negativo: " + amount + " - " + other.amount);
        }
        return new Money(amount.subtract(other.amount));
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}