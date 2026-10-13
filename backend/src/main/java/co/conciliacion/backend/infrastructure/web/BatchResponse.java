package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.LineResult;
import co.conciliacion.backend.application.common.PageResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

public record BatchResponse(String loteId, String origen, LocalDate fechaGeneracion, String estado,
                            int totalLineas, int aplicadas, int rechazadas, boolean reenvio,
                            PageResponse<LineResponse> lineas) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LineResponse(int numeroLinea, String referencia, String numeroFactura,
                               String valor, String resultado, String motivo, String detalle) {
        static LineResponse from(LineResult l) {
            return new LineResponse(l.lineNumber(), l.reference(), l.invoiceNumber(), l.amount(),
                l.outcome(), l.reason(), l.detail());
        }
    }

    static BatchResponse from(BatchSummary s, PageResult<LineResult> page) {
        return new BatchResponse(s.batchId(), s.origin(), s.generatedAt(), s.status(),
            s.totalLines(), s.appliedLines(), s.rejectedLines(), s.replay(),
            new PageResponse<>(page.items().stream().map(LineResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages()));
    }
}