package co.conciliacion.backend.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Las lineas llegan como strings: una linea mal formada se rechaza sola, no tumba el lote. */
public record BatchRequest(
    @NotBlank String loteId,
    @NotBlank String origen,
    LocalDate fechaGeneracion,
    @NotEmpty @Size(max = 100_000) @Valid List<Line> pagos) {

    public record Line(String referencia, String numeroFactura, String valor, String fechaPago) { }
}