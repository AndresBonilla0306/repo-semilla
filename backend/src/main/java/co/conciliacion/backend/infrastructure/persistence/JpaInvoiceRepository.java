package co.conciliacion.backend.infrastructure.persistence;

import co.conciliacion.backend.application.common.PageResult;
import co.conciliacion.backend.application.port.out.InvoiceQueryPort;
import co.conciliacion.backend.application.port.out.InvoiceRepository;
import co.conciliacion.backend.domain.model.Invoice;
import co.conciliacion.backend.domain.model.InvoiceStatus;
import co.conciliacion.backend.domain.model.Money;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInvoiceRepository implements InvoiceRepository, InvoiceQueryPort {

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
                invoice.total().amount(), invoice.balance().amount(), invoice.status().name()));
        entity.setBalance(invoice.balance().amount());
        entity.setStatus(invoice.status().name());
        jpa.save(entity);
    }

    @Override
    public PageResult<Invoice> search(String nit, InvoiceStatus status, int page, int size) {
        Specification<InvoiceEntity> spec = (root, query, cb) -> cb.conjunction();
        if (nit != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("supplierNit"), nit));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> statusPredicate(status, root, cb));
        }
        // Orden por clave unica e inmutable: la paginacion no se mueve cuando se aplican pagos (PR-03).
        Page<InvoiceEntity> result = jpa.findAll(spec, PageRequest.of(page, size, Sort.by("number")));
        return new PageResult<>(result.map(this::toDomain).getContent(), page, size, result.getTotalElements());
    }

    private Predicate statusPredicate(InvoiceStatus status, Root<InvoiceEntity> root, CriteriaBuilder cb) {
        // Path<BigDecimal> balance = root.get("balance");
        // Path<BigDecimal> total = root.get("total");
        // return switch (status) {
        //     case PAID -> cb.equal(balance, BigDecimal.ZERO);
        //     case PENDING -> cb.equal(balance, total);
        //     case PARTIAL -> cb.and(cb.greaterThan(balance, BigDecimal.ZERO), cb.lessThan(balance, total));
        return cb.equal(root.get("status"), status.name());
        };

    private Invoice toDomain(InvoiceEntity e) {
        return Invoice.rehydrate(e.getNumber(), e.getSupplierNit(), e.getSupplierName(),
            e.getIssueDate(), e.getDueDate(), new Money(e.getTotal()), new Money(e.getBalance()),
            InvoiceStatus.valueOf(e.getStatus()));
    }
}