package co.conciliacion.backend.application.batch;

import java.time.LocalDate;
import java.util.List;

public record BatchCommand(String batchId, String origin, LocalDate generatedAt, List<RawLine> lines) { }