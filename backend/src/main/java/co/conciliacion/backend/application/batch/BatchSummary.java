package co.conciliacion.backend.application.batch;

import java.time.LocalDate;

public record BatchSummary(String batchId, String origin, LocalDate generatedAt, String contentHash,
                           String status, int totalLines, int appliedLines, int rejectedLines,
                           boolean replay) {
    public BatchSummary withReplay(boolean value) {
        return new BatchSummary(batchId, origin, generatedAt, contentHash, status,
            totalLines, appliedLines, rejectedLines, value);
    }
}