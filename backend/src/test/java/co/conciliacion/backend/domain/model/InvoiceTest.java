package co.conciliacion.backend.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private static final OffsetDateTime ON_TIME = OffsetDateTime.parse("2026-03-10T10:00:00-05:00");

    /** Vence el 2026-03-15. */
    private Invoice invoice(String total) {
        return Invoice.issue("FV-1", "900123456", "Proveedor SA",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), Money.of(total));
    }

    private OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test
    void initialBalanceEqualsTotalAndStatusIsPending() {
        Invoice i = invoice("1000.50");
        assertEquals(i.total(), i.balance());
        assertEquals(InvoiceStatus.PENDING, i.status());
    }

    @Test
    void applyPaymentReducesBalanceAndKeepsOriginalUntouched() {
        Invoice original = invoice("1000");
        Invoice updated = original.applyPayment(Money.of("400"), ON_TIME);
        assertEquals(Money.of("600"), updated.balance());
        assertEquals(Money.of("1000"), original.balance());
    }

    @Test
    void paymentExceedingBalanceIsNotAppliedPartially() {
        Invoice i = invoice("100");
        assertThrows(IllegalArgumentException.class,
            () -> i.applyPayment(Money.of("100.01"), ON_TIME));
    }

    @Test
    void rejectsDueDateBeforeIssueDate() {
        assertThrows(IllegalArgumentException.class, () -> Invoice.issue("FV-1", "1", "X",
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 1), Money.of("10")));
    }

    @Test
    void statusFollowsPayments() {
        Invoice i = invoice("100");
        assertEquals(InvoiceStatus.PARTIAL, i.applyPayment(Money.of("40"), ON_TIME).status());
        assertEquals(InvoiceStatus.PAID, i.applyPayment(Money.of("100"), ON_TIME).status());
    }

    @Test // CF-05
    void cf05_paymentAt2330ColombiaOnDueDateIsNotLate() {
        Invoice paid = invoice("100").applyPayment(Money.of("100"), at("2026-03-15T23:30:00-05:00"));
        assertEquals(InvoiceStatus.PAID, paid.status());
    }

    @Test // el mismo instante expresado en UTC da el mismo resultado
    void sameInstantInUtcGivesSameResult() {
        Invoice paid = invoice("100").applyPayment(Money.of("100"), at("2026-03-16T04:30:00Z"));
        assertEquals(InvoiceStatus.PAID, paid.status());
    }

    @Test // frontera exacta: 04:59:59Z es 23:59:59 en Colombia, 05:00:00Z ya es el dia siguiente
    void lateBoundaryIsMidnightInColombia() {
        assertEquals(InvoiceStatus.PAID,
            invoice("100").applyPayment(Money.of("100"), at("2026-03-16T04:59:59Z")).status());
        assertEquals(InvoiceStatus.PAID_LATE,
            invoice("100").applyPayment(Money.of("100"), at("2026-03-16T05:00:00Z")).status());
    }

    @Test
    void paymentAfterDueDateSettlesAsPaidLate() {
        Invoice paid = invoice("100").applyPayment(Money.of("100"), at("2026-03-20T09:00:00-05:00"));
        assertEquals(InvoiceStatus.PAID_LATE, paid.status());
    }

    @Test // un pago tardio que no salda la factura no cambia el estado a tardia
    void latePartialPaymentStaysPartial() {
        Invoice partial = invoice("100").applyPayment(Money.of("40"), at("2026-03-20T09:00:00-05:00"));
        assertEquals(InvoiceStatus.PARTIAL, partial.status());
    }

    @Test
    void inconsistentStatusAndBalanceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Invoice.rehydrate("FV-1", "1", "X",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15),
            Money.of("100"), Money.of("50"), InvoiceStatus.PAID));
    }
}