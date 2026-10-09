package co.conciliacion.backend.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Entidad JPA: existe solo en infraestructura. El dominio no la conoce. */
@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    @Column(name = "number", length = 20)
    private String number;

    @Column(name = "supplier_nit", nullable = false)
    private String supplierNit;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected InvoiceEntity() { } // requerido por JPA

    public InvoiceEntity(String number, String supplierNit, String supplierName,
                         LocalDate issueDate, LocalDate dueDate,
                         BigDecimal total, BigDecimal balance) {
        this.number = number;
        this.supplierNit = supplierNit;
        this.supplierName = supplierName;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.total = total;
        this.balance = balance;
    }

    public String getNumber() { return number; }
    public String getSupplierNit() { return supplierNit; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}