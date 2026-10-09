package co.conciliacion.backend.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToTwoDecimals() {
        assertEquals("37500483.00", Money.of("37500483").toString());
        assertEquals("37500483.40", Money.of("37500483.4").toString());
    }

    @Test
    void rejectsMoreThanTwoSignificantDecimals() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("10.001"));
    }

    @Test
    void acceptsRedundantTrailingZeros() {
        assertEquals(Money.of("10.00"), Money.of("10.0000"));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("-1"));
    }

    @Test
    void doesNotLoseCentsWhereDoubleWould() {
        Money sum = Money.of("0.10").plus(Money.of("0.20"));
        assertEquals(new BigDecimal("0.30"), sum.amount());
    }

    @Test
    void minusBeyondZeroFails() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1").minus(Money.of("2")));
    }
}