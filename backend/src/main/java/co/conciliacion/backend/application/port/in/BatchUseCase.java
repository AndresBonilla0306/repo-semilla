package co.conciliacion.backend.application.port.in;

import co.conciliacion.backend.application.batch.BatchCommand;
import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.LineResult;
import co.conciliacion.backend.application.common.PageResult;
import java.util.List;

public interface BatchUseCase {
    BatchSummary process(BatchCommand command);
    BatchSummary summary(String batchId);
    PageResult<LineResult> lines(String batchId, String reason, int page, int size);
    List<LineResult> allLines(String batchId);
}