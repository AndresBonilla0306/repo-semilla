package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.domain.model.Money;
import co.conciliacion.backend.domain.model.Payment;
import co.conciliacion.backend.domain.model.PaymentResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pagos")
public class PaymentController {

    private final ApplyPaymentUseCase useCase;

    public PaymentController(ApplyPaymentUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> apply(@Valid @RequestBody ApplyPaymentRequest request) {
        Payment payment = new Payment(request.reference(), request.invoiceNumber(),
            Money.of(request.amount()), request.paidAt());
        PaymentResult result = useCase.apply(payment);
        HttpStatus status = result instanceof PaymentResult.Applied
            ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(PaymentResponse.from(result));
    }
}