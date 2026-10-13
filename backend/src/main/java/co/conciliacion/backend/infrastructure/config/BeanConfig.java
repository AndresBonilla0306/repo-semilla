package co.conciliacion.backend.infrastructure.config;

import co.conciliacion.backend.application.port.in.ApplyPaymentUseCase;
import co.conciliacion.backend.application.port.in.SearchInvoicesUseCase;
import co.conciliacion.backend.application.port.out.AuditRepository;
import co.conciliacion.backend.application.port.out.InvoiceQueryPort;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.application.port.out.PaymentRepository;
import co.conciliacion.backend.application.service.ApplyPaymentService;
import co.conciliacion.backend.application.service.SearchInvoicesService;
import co.conciliacion.backend.infrastructure.transaction.TransactionalApplyPaymentUseCase;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ApplyPaymentUseCase applyPaymentUseCase(InvoiceRepository invoices,
                                                   PaymentRepository payments,
                                                   AuditRepository audit,
                                                   Clock clock,
                                                   @Value("${app.audit.user:tesoreria}") String actor) {
        return new TransactionalApplyPaymentUseCase(
            new ApplyPaymentService(invoices, payments, audit, clock, actor));
    }

    @Bean
    public SearchInvoicesUseCase searchInvoicesUseCase(InvoiceQueryPort query) {
        return new SearchInvoicesService(query);
    }
}