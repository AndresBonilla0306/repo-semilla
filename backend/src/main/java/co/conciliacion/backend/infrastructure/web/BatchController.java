package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.batch.BatchCommand;
import co.conciliacion.backend.application.batch.BatchSummary;
import co.conciliacion.backend.application.batch.LineResult;
import co.conciliacion.backend.application.batch.RawLine;
import co.conciliacion.backend.application.port.in.BatchUseCase;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/lotes")
public class BatchController {

    private static final int PREVIEW_SIZE = 50;
    private final BatchUseCase useCase;

    public BatchController(BatchUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<BatchResponse> submit(@Valid @RequestBody BatchRequest request) {
        List<BatchRequest.Line> pagos = request.pagos();
        List<RawLine> lines = IntStream.range(0, pagos.size())
            .mapToObj(i -> new RawLine(i + 1, pagos.get(i).referencia(), pagos.get(i).numeroFactura(),
                pagos.get(i).valor(), pagos.get(i).fechaPago(), null))
            .toList();
        return run(new BatchCommand(request.loteId().strip(), request.origen().strip(),
            request.fechaGeneracion(), lines));
    }

    @PostMapping(value = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String loteId,
            @RequestParam(defaultValue = "TESORERIA") String origen,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaGeneracion)
            throws IOException {
        byte[] bytes = file.getBytes();
        String id = (loteId == null || loteId.isBlank()) ? "CSV-" + fingerprint(bytes) : loteId.strip();
        return run(new BatchCommand(id, origen.strip(), fechaGeneracion, CsvBatchParser.parse(bytes)));
    }

    @GetMapping("/{id}")
    public BatchResponse detail(@PathVariable String id,
                                @RequestParam(required = false) String reason,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        return BatchResponse.from(useCase.summary(id), useCase.lines(id, reason, page, size));
    }

    @GetMapping("/{id}/resultado")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        List<LineResult> lines = useCase.allLines(id);
        StringBuilder sb = new StringBuilder("numero_linea;referencia;numero_factura;valor;resultado;motivo;detalle\n");
        for (LineResult l : lines) {
            sb.append(l.lineNumber()).append(';').append(cell(l.reference())).append(';')
              .append(cell(l.invoiceNumber())).append(';').append(cell(l.amount())).append(';')
              .append(l.outcome()).append(';').append(cell(l.reason())).append(';')
              .append(cell(l.detail())).append('\n');
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"resultado-" + id + ".csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<BatchResponse> run(BatchCommand command) {
        BatchSummary summary = useCase.process(command);
        BatchResponse body = BatchResponse.from(summary, useCase.lines(summary.batchId(), null, 0, PREVIEW_SIZE));
        return ResponseEntity.status(summary.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    private static String cell(String value) {
        if (value == null) return "";
        boolean needsQuotes = value.contains(";") || value.contains("\"") || value.contains("\n");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static String fingerprint(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}