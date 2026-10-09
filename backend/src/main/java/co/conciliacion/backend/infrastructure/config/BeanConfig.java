package co.conciliacion.backend.infrastructure.config;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.application.service.ApplyPaymentService;
import co.conciliacion.backend.infrastructure.transaction.TransactionalApplyPaymentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public ApplyPaymentUseCase applyPaymentUseCase(InvoiceRepository invoices) {
        return new TransactionalApplyPaymentUseCase(new ApplyPaymentService(invoices));
    }
}