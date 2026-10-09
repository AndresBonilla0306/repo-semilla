package co.conciliacion.backend.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private Invoice invoice(String total) {
        return Invoice.issue("FV-1", "900123456", "Proveedor SA",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), Money.of(total));
    }

    @Test
    void initialBalanceEqualsTotal() {
        Invoice i = invoice("1000.50");
        assertEquals(i.total(), i.balance());
    }

    @Test
    void applyPaymentReducesBalanceAndKeepsOriginalUntouched() {
        Invoice original = invoice("1000");
        Invoice updated = original.applyPayment(Money.of("400"));
        assertEquals(Money.of("600"), updated.balance());
        assertEquals(Money.of("1000"), original.balance());
    }

    @Test
    void paymentExceedingBalanceIsNotAppliedPartially() {
        Invoice i = invoice("100");
        assertThrows(IllegalArgumentException.class, () -> i.applyPayment(Money.of("100.01")));
    }

    @Test
    void rejectsDueDateBeforeIssueDate() {
        assertThrows(IllegalArgumentException.class, () -> Invoice.issue("FV-1", "1", "X",
            LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 1), Money.of("10")));
    }

    @Test
    void statusIsDerivedFromBalance() {
        Invoice i = invoice("100");
        assertEquals(InvoiceStatus.PENDING, i.status());
        assertEquals(InvoiceStatus.PARTIAL, i.applyPayment(Money.of("40")).status());
        assertEquals(InvoiceStatus.PAID, i.applyPayment(Money.of("100")).status());
    }
}