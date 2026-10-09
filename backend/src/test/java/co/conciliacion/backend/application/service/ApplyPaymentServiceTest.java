package co.conciliacion.backend.application.service;

import static org.junit.jupiter.api.Assertions.*;

import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.domain.model.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplyPaymentServiceTest {

    /** Repositorio en memoria solo para pruebas: el dominio no necesita base de datos. */
    static class FakeInvoiceRepository implements InvoiceRepository {
        final Map<String, Invoice> store = new HashMap<>();
        int saves = 0;

        @Override public Optional<Invoice> findByNumberForUpdate(String number) {
            return Optional.ofNullable(store.get(number));
        }
        @Override public void save(Invoice invoice) {
            saves++;
            store.put(invoice.number(), invoice);
        }
    }

    private FakeInvoiceRepository repo;
    private ApplyPaymentService service;

    @BeforeEach
    void setUp() {
        repo = new FakeInvoiceRepository();
        service = new ApplyPaymentService(repo);
        repo.store.put("FV-1", Invoice.issue("FV-1", "900123456", "Proveedor SA",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), Money.of("37500483.40")));
    }

    private Payment payment(String invoice, String amount) {
        return new Payment("P-1", invoice, Money.of(amount), OffsetDateTime.parse("2026-03-15T10:00:00-05:00"));
    }

    @Test
    void appliesPaymentAndReducesBalance() {
        PaymentResult r = service.apply(payment("FV-1", "1000"));
        PaymentResult.Applied applied = assertInstanceOf(PaymentResult.Applied.class, r);
        assertEquals(Money.of("37499483.40"), applied.invoice().balance());
        assertEquals(Money.of("37500483.40"), applied.previousBalance());
        assertEquals(1, repo.saves);
    }

    @Test
    void rejectsPaymentForMissingInvoice() {
        PaymentResult r = service.apply(payment("FV-NOPE", "1000"));
        assertEquals(RejectionReason.INVOICE_NOT_FOUND,
            assertInstanceOf(PaymentResult.Rejected.class, r).reason());
        assertEquals(0, repo.saves);
    }

    @Test // CF-01 (lectura literal): queda un residuo de 0,40
    void cf01_paymentOfIntegerPartLeavesResidue() {
        PaymentResult r = service.apply(payment("FV-1", "37500483"));
        PaymentResult.Applied applied = assertInstanceOf(PaymentResult.Applied.class, r);
        assertEquals(Money.of("0.40"), applied.invoice().balance());
    }

    @Test // CF-02 / RF-09: se rechaza completo y el saldo no cambia (RF-11)
    void cf02_paymentExceedingBalanceIsRejectedAndBalanceUntouched() {
        PaymentResult r = service.apply(payment("FV-1", "37500484"));
        assertEquals(RejectionReason.AMOUNT_EXCEEDS_BALANCE,
            assertInstanceOf(PaymentResult.Rejected.class, r).reason());
        assertEquals(0, repo.saves);
        assertEquals(Money.of("37500483.40"), repo.store.get("FV-1").balance());
    }

    @Test
    void paymentExactlyEqualToBalanceIsAccepted() {
        PaymentResult r = service.apply(payment("FV-1", "37500483.40"));
        PaymentResult.Applied applied = assertInstanceOf(PaymentResult.Applied.class, r);
        assertTrue(applied.invoice().balance().isZero());
    }
}