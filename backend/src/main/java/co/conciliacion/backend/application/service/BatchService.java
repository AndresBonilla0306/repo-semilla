package co.conciliacion.backend.application.service;

import co.conciliacion.backend.application.batch.*;
import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.in.BatchUseCase;
import co.conciliacion.backend.application.port.out.BatchRepository;
import co.conciliacion.backend.domain.model.Money;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import co.conciliacion.backend.domain.model.RejectionReason;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;


public class BatchService implements BatchUseCase {

    private static final Pattern AMOUNT = Pattern.compile("\\d{1,15}");
    private static final int MAX_PAGE_SIZE = 200;

    private final ApplyPaymentUseCase applyPayment;
    private final BatchRepository batches;
    private final Clock clock;

    public BatchService(ApplyPaymentUseCase applyPayment, BatchRepository batches, Clock clock) {
        this.applyPayment = applyPayment;
        this.batches = batches;
        this.clock = clock;
    }

    @Override
    public BatchSummary process(BatchCommand cmd) {
        String hash = hash(cmd.lines());
        Optional<BatchSummary> existing = batches.find(cmd.batchId());
        if (existing.isPresent()) {
            return resend(existing.get(), hash);
        }
        if (!batches.begin(cmd.batchId(), cmd.origin(), cmd.generatedAt(), hash, clock.instant())) {
            // Otro hilo lo registro entre el find y el begin.
            return resend(batches.find(cmd.batchId()).orElseThrow(), hash);
        }
        List<LineResult> results = new ArrayList<>(cmd.lines().size());
        int applied = 0;
        for (RawLine raw : cmd.lines()) {
            LineResult result = processLine(raw);
            results.add(result);
            if ("APPLIED".equals(result.outcome())) {
                applied++;
            }
        }
        batches.finish(cmd.batchId(), results, applied, results.size() - applied);
        return batches.find(cmd.batchId()).orElseThrow();
    }

    /** Reenvio (RF-20): mismo contenido devuelve lo guardado; contenido distinto es un conflicto. */
    private BatchSummary resend(BatchSummary stored, String hash) {
        if (!stored.contentHash().equals(hash)) {
            throw new BatchConflictException(
                "El lote " + stored.batchId() + " ya existe con un contenido distinto");
        }
        if (!"DONE".equals(stored.status())) {
            throw new BatchConflictException("El lote " + stored.batchId() + " se esta procesando");
        }
        return stored.withReplay(true);
    }

    private LineResult processLine(RawLine raw) {
        if (raw.formatError() != null) {
            return invalid(raw, raw.formatError());
        }
        if (isBlank(raw.reference())) return invalid(raw, "La referencia esta vacia");
        if (isBlank(raw.invoiceNumber())) return invalid(raw, "El numero de factura esta vacio");
        String amountText = raw.amount() == null ? "" : raw.amount().strip();
        if (!AMOUNT.matcher(amountText).matches()) {
            return invalid(raw, "El valor debe ser un entero sin separadores ni decimales: '"
                + raw.amount() + "'");
        }
        Money amount = Money.of(amountText);
        if (amount.isZero()) return invalid(raw, "El valor debe ser mayor que cero");
        OffsetDateTime paidAt;
        try {
            paidAt = OffsetDateTime.parse(raw.paidAt() == null ? "" : raw.paidAt().strip());
        } catch (DateTimeParseException e) {
            return invalid(raw, "Fecha invalida o sin offset: '" + raw.paidAt() + "'");
        }
        Payment payment = new Payment(raw.reference(), raw.invoiceNumber(), amount, paidAt);
        PaymentResult result = applyPayment.apply(payment);
        return switch (result) {
            case PaymentResult.Applied a -> new LineResult(raw.number(), payment.reference(),
                payment.invoiceNumber(), amountText, "APPLIED", null, null);
            case PaymentResult.Rejected r -> new LineResult(raw.number(), payment.reference(),
                payment.invoiceNumber(), amountText, "REJECTED", r.reason().name(), r.detail());
        };
    }

    private LineResult invalid(RawLine raw, String detail) {
        return new LineResult(raw.number(), raw.reference(), raw.invoiceNumber(), raw.amount(),
            "REJECTED", RejectionReason.INVALID_LINE.name(), detail);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public BatchSummary summary(String batchId) {
        return batches.find(batchId).orElseThrow(() -> new BatchNotFoundException(batchId));
    }

    @Override
    public PageResult<LineResult> lines(String batchId, String reason, int page, int size) {
        summary(batchId);
        String cleanReason = (reason == null || reason.isBlank()) ? null : reason.strip();
        return batches.lines(batchId, cleanReason, Math.max(page, 0),
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @Override
    public List<LineResult> allLines(String batchId) {
        summary(batchId);
        return batches.allLines(batchId);
    }

    private static String hash(List<RawLine> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RawLine l : lines) {
                String row = l.reference() + "|" + l.invoiceNumber() + "|" + l.amount() + "|"
                    + l.paidAt() + "|" + l.formatError() + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}