package co.conciliacion.backend.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

public record ApplyPaymentRequest(
    @NotBlank String reference,
    @NotBlank String invoiceNumber,
    // RF-10: Tesoreria opera en pesos enteros. El monto viaja como string (RF-05).
    @NotBlank @Pattern(regexp = "\\d{1,15}",
        message = "El valor debe ser un entero sin separadores ni decimales") String amount,
    @NotNull OffsetDateTime paidAt) { }