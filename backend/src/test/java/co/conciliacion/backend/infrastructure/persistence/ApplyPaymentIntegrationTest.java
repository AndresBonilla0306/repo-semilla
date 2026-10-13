package co.conciliacion.backend.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.domain.model.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ApplyPaymentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ApplyPaymentUseCase useCase;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from payment_audit");
        jdbc.update("delete from payment");
        jdbc.update("delete from invoice");
        jdbc.update("""
            insert into invoice(number, supplier_nit, supplier_name, issue_date, due_date, total, balance)
            values ('FV-1', '800404150-7', 'Proveedor SA', '2026-03-01', '2026-03-15', 100.00, 100.00)""");
    }

    private Payment payment(String ref, String amount) {
        return new Payment(ref, "FV-1", Money.of(amount), OffsetDateTime.parse("2026-03-15T10:00:00-05:00"));
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("select balance from invoice where number = 'FV-1'", BigDecimal.class);
    }

    @Test
    void appliesPaymentAndPersistsBalance() {
        assertInstanceOf(PaymentResult.Applied.class, useCase.apply(payment("P-1", "40")));
        assertEquals(new BigDecimal("60.00"), balance());
    }

    @Test
    void rejectedPaymentLeavesBalanceUntouched() {
        assertInstanceOf(PaymentResult.Rejected.class, useCase.apply(payment("P-1", "100.01")));
        assertEquals(new BigDecimal("100.00"), balance());
    }

    @Test // el bloqueo funciona: con 60 y 70 sobre saldo 100, solo uno cabe
    void concurrentPaymentsNeverExceedBalance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<PaymentResult>> tasks = List.of(
            () -> { start.await(); return useCase.apply(payment("P-A", "60")); },
            () -> { start.await(); return useCase.apply(payment("P-B", "70")); });
        List<Future<PaymentResult>> futures = tasks.stream().map(pool::submit).toList();
        start.countDown();

        long applied = 0;
        for (Future<PaymentResult> f : futures) {
            if (f.get(10, TimeUnit.SECONDS) instanceof PaymentResult.Applied) applied++;
        }
        pool.shutdown();

        assertEquals(1, applied);
        assertTrue(balance().compareTo(new BigDecimal("30.00")) == 0
                || balance().compareTo(new BigDecimal("40.00")) == 0);
    }

    @Test // RF-12 contra la base real
    void sameReferenceCannotBeAppliedTwice() {
        assertInstanceOf(PaymentResult.Applied.class, useCase.apply(payment("P-1", "40")));
        PaymentResult second = useCase.apply(payment("P-1", "40"));
        assertEquals(RejectionReason.DUPLICATE_REFERENCE,
            assertInstanceOf(PaymentResult.Rejected.class, second).reason());
        assertEquals(new BigDecimal("60.00"), balance());
        assertEquals(1, jdbc.queryForObject("select count(*) from payment", Integer.class));
    }

    @Test // dos hilos con la MISMA referencia: gracias al bloqueo, solo uno la aplica
    void concurrentSameReferenceIsAppliedOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResult>> futures = List.of(
            pool.submit(() -> { start.await(); return useCase.apply(payment("P-X", "40")); }),
            pool.submit(() -> { start.await(); return useCase.apply(payment("P-X", "40")); }));
        start.countDown();

        long applied = 0;
        for (Future<PaymentResult> f : futures) {
            if (f.get(10, TimeUnit.SECONDS) instanceof PaymentResult.Applied) applied++;
        }
        pool.shutdown();

        assertEquals(1, applied);
        assertEquals(new BigDecimal("60.00"), balance());
    }

    @Test // CF-05 de punta a punta, incluida la columna status
    void cf05_settlingAt2330ColombiaIsPaidNotLate() {
        Payment p = new Payment("P-C5", "FV-1", Money.of("100"),
            OffsetDateTime.parse("2026-03-15T23:30:00-05:00"));
        assertInstanceOf(PaymentResult.Applied.class, useCase.apply(p));
        assertEquals("PAID",
            jdbc.queryForObject("select status from invoice where number = 'FV-1'", String.class));
    }

    @Test
    void settlingAfterDueDateIsPaidLate() {
        Payment p = new Payment("P-L1", "FV-1", Money.of("100"),
            OffsetDateTime.parse("2026-03-16T08:00:00-05:00"));
        assertInstanceOf(PaymentResult.Applied.class, useCase.apply(p));
        assertEquals("PAID_LATE",
            jdbc.queryForObject("select status from invoice where number = 'FV-1'", String.class));
    }

        @Test // la auditoria llega a la base real, con saldos exactos
    void appliedPaymentIsAuditedInDatabase() {
        useCase.apply(payment("P-1", "40"));
        var row = jdbc.queryForMap(
            "select outcome, balance_before, balance_after, performed_by from payment_audit");
        assertEquals("APPLIED", row.get("outcome"));
        assertEquals(new BigDecimal("100.00"), row.get("balance_before"));
        assertEquals(new BigDecimal("60.00"), row.get("balance_after"));
        assertEquals("tesoreria", row.get("performed_by"));
    }

    @Test // el rechazo no revierte la transaccion: la auditoria sobrevive
    void rejectedPaymentIsAuditedAndBalanceUntouched() {
        useCase.apply(payment("P-2", "100.01"));
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from payment_audit where outcome = 'REJECTED'", Integer.class));
        assertEquals(new BigDecimal("100.00"), balance());
    }
}