package co.conciliacion.backend.infrastructure.web;

import co.conciliacion.backend.domain.model.PaymentResult;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(String status, String reason, String detail,
                              String previousBalance, String balance) {

    static PaymentResponse from(PaymentResult result) {
        return switch (result) {
            case PaymentResult.Applied a -> new PaymentResponse("APPLIED", null, null,
                a.previousBalance().toString(), a.invoice().balance().toString());
            case PaymentResult.Rejected r -> new PaymentResponse("REJECTED",
                r.reason().name(), r.detail(), null, null);
        };
    }
}