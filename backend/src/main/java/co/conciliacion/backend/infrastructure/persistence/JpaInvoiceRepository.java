package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.Money;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInvoiceRepository implements InvoiceRepository {

    private final InvoiceJpaRepository jpa;

    public JpaInvoiceRepository(InvoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Invoice> findByNumberForUpdate(String number) {
        return jpa.findByNumberForUpdate(number).map(this::toDomain);
    }

    @Override
    public void save(Invoice invoice) {
        InvoiceEntity entity = jpa.findById(invoice.number())
            .orElseGet(() -> new InvoiceEntity(invoice.number(), invoice.supplierNit(),
                invoice.supplierName(), invoice.issueDate(), invoice.dueDate(),
                invoice.total().amount(), invoice.balance().amount()));
        entity.setBalance(invoice.balance().amount());
        jpa.save(entity);
    }

    private Invoice toDomain(InvoiceEntity e) {
        return Invoice.rehydrate(e.getNumber(), e.getSupplierNit(), e.getSupplierName(),
            e.getIssueDate(), e.getDueDate(), new Money(e.getTotal()), new Money(e.getBalance()));
    }
}