package co.conciliacion.backend.application.port.out;

import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.LineResult;
import co.conciliacion.backend.application.common.PageResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchRepository {
    Optional<BatchSummary> find(String batchId);

    /** Registra el lote como PROCESSING. Devuelve false si el id ya existia. */
    boolean begin(String batchId, String origin, LocalDate generatedAt, String contentHash, Instant now);

    /** Guarda las lineas y marca el lote DONE en una sola transaccion. */
    void finish(String batchId, List<LineResult> lines, int applied, int rejected);

    PageResult<LineResult> lines(String batchId, String reason, int page, int size);

    List<LineResult> allLines(String batchId);
}