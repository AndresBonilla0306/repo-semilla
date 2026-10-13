package co.conciliacion.backend.application.service;

import static org.junit.jupiter.api.Assertions.*;

import co.conciliacion.backend.application.batch.*;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.out.BatchRepository;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchServiceTest {

    static class FakeBatchRepository implements BatchRepository {
        final Map<String, BatchSummary> headers = new HashMap<>();
        final Map<String, List<LineResult>> lines = new HashMap<>();

        @Override public Optional<BatchSummary> find(String id) { return Optional.ofNullable(headers.get(id)); }
        @Override public boolean begin(String id, String origin, LocalDate g, String hash, Instant now) {
            if (headers.containsKey(id)) return false;
            headers.put(id, new BatchSummary(id, origin, g, hash, "PROCESSING", 0, 0, 0, false));
            return true;
        }
        @Override public void finish(String id, List<LineResult> l, int applied, int rejected) {
            BatchSummary h = headers.get(id);
            lines.put(id, l);
            headers.put(id, new BatchSummary(id, h.origin(), h.generatedAt(), h.contentHash(),
                "DONE", l.size(), applied, rejected, false));
        }
        @Override public PageResult<LineResult> lines(String id, String reason, int page, int size) {
            List<LineResult> filtered = lines.get(id).stream()
                .filter(l -> reason == null || reason.equals(l.reason())).toList();
            return new PageResult<>(filtered, page, size, filtered.size());
        }
        @Override public List<LineResult> allLines(String id) { return lines.get(id); }
    }

    private ApplyPaymentServiceTest.FakeInvoiceRepository invoices;
    private FakeBatchRepository batches;
    private BatchService service;

    @BeforeEach
    void setUp() {
        invoices = new ApplyPaymentServiceTest.FakeInvoiceRepository();
        batches = new FakeBatchRepository();
        ApplyPaymentService apply = new ApplyPaymentService(invoices,
            new ApplyPaymentServiceTest.FakePaymentRepository(),
            new ApplyPaymentServiceTest.FakeAuditRepository(),
            Clock.fixed(Instant.parse("2026-03-15T15:00:00Z"), ZoneOffset.UTC), "test");
        service = new BatchService(apply, batches, Clock.systemUTC());
        invoices.store.put("FV-1", Invoice.issue("FV-1", "900", "Proveedor SA",
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15), Money.of("100")));
    }

    private RawLine line(int n, String ref, String invoice, String amount) {
        return new RawLine(n, ref, invoice, amount, "2026-03-15T10:00:00-05:00", null);
    }

    private BatchCommand batch(String id, RawLine... lines) {
        return new BatchCommand(id, "TESORERIA", LocalDate.of(2026, 3, 15), List.of(lines));
    }

    @Test // CF-06: se aplica en el orden del lote
    void cf06_linesAreAppliedInBatchOrder() {
        service.process(batch("L-1", line(1, "A", "FV-1", "60"), line(2, "B", "FV-1", "70")));
        List<LineResult> r = batches.allLines("L-1");
        assertEquals("APPLIED", r.get(0).outcome());
        assertEquals("AMOUNT_EXCEEDS_BALANCE", r.get(1).reason());
        assertEquals(Money.of("40"), invoices.store.get("FV-1").balance());
    }

    @Test // CF-03: el reenvio devuelve lo guardado y no toca saldos
    void cf03_resendReturnsStoredResultWithoutReapplying() {
        BatchCommand cmd = batch("L-1", line(1, "A", "FV-1", "60"));
        BatchSummary first = service.process(cmd);
        BatchSummary second = service.process(cmd);
        assertFalse(first.replay());
        assertTrue(second.replay());
        assertEquals(first.appliedLines(), second.appliedLines());
        assertEquals(Money.of("40"), invoices.store.get("FV-1").balance());
        assertEquals(1, invoices.saves);
    }

    @Test
    void sameIdWithDifferentContentIsAConflict() {
        service.process(batch("L-1", line(1, "A", "FV-1", "60")));
        assertThrows(BatchConflictException.class,
            () -> service.process(batch("L-1", line(1, "A", "FV-1", "61"))));
    }

    @Test // RF-21: una linea mala no tumba las demas
    void invalidLinesAreRejectedIndividually() {
        service.process(batch("L-1",
            line(1, "A", "FV-1", "10.50"),
            line(2, "B", "FV-1", "abc"),
            new RawLine(3, "C", "FV-1", "10", "no-es-fecha", null),
            new RawLine(4, null, null, null, null, "Se esperaban 4 columnas y llegaron 3"),
            line(5, "D", "FV-1", "20")));
        List<LineResult> r = batches.allLines("L-1");
        assertEquals(4, r.stream().filter(l -> "INVALID_LINE".equals(l.reason())).count());
        assertEquals("APPLIED", r.get(4).outcome());
        assertEquals(Money.of("80"), invoices.store.get("FV-1").balance());
    }

    @Test
    void missingInvoiceAndDuplicateReferenceInsideBatchAreReportedPerLine() {
        service.process(batch("L-1",
            line(1, "A", "FV-1", "10"),
            line(2, "A", "FV-1", "10"),
            line(3, "B", "FV-NOPE", "10")));
        List<LineResult> r = batches.allLines("L-1");
        assertEquals("DUPLICATE_REFERENCE", r.get(1).reason());
        assertEquals("INVOICE_NOT_FOUND", r.get(2).reason());
    }

    @Test
    void unknownBatchThrowsNotFound() {
        assertThrows(BatchNotFoundException.class, () -> service.summary("NOPE"));
    }
}