package co.conciliacion.backend.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Factura de un proveedor. Inmutable: aplicar un pago devuelve una factura nueva. */
public final class Invoice {

    /** Zona del negocio. Explicita: nunca se usa la zona por defecto de la JVM (RF-13). */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private final String number;
    private final String supplierNit;
    private final String supplierName;
    private final LocalDate issueDate;
    private final LocalDate dueDate;
    private final Money total;
    private final Money balance;
    private final InvoiceStatus status;

    private Invoice(String number, String supplierNit, String supplierName,
                    LocalDate issueDate, LocalDate dueDate, Money total, Money balance,
                    InvoiceStatus status) {
        this.number = requireText(number, "number");
        this.supplierNit = requireText(supplierNit, "supplierNit");
        this.supplierName = requireText(supplierName, "supplierName");
        this.issueDate = Objects.requireNonNull(issueDate, "issueDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.total = Objects.requireNonNull(total, "total");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.status = Objects.requireNonNull(status, "status");
        if (total.isZero()) {
            throw new IllegalArgumentException("El valor total debe ser mayor que cero");
        }
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a la emision");
        }
        if (balance.isGreaterThan(total)) {
            throw new IllegalArgumentException("El saldo no puede superar el valor total");
        }
        requireConsistentStatus();
    }

    /** El estado debe ser coherente con el saldo; si no, el dato esta corrupto. */
    private void requireConsistentStatus() {
        boolean consistent = switch (status) {
            case PENDING -> balance.equals(total);
            case PARTIAL -> !balance.isZero() && !balance.equals(total);
            case PAID, PAID_LATE -> balance.isZero();
        };
        if (!consistent) {
            throw new IllegalArgumentException(
                "Estado " + status + " incoherente con saldo " + balance + " y total " + total);
        }
    }

    /** Factura nueva: el saldo pendiente inicial es igual al total (RF-02). */
    public static Invoice issue(String number, String supplierNit, String supplierName,
                                LocalDate issueDate, LocalDate dueDate, Money total) {
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate,
            total, total, InvoiceStatus.PENDING);
    }

    /** Reconstruye una factura ya existente (uso del adaptador de persistencia). */
    public static Invoice rehydrate(String number, String supplierNit, String supplierName,
                                    LocalDate issueDate, LocalDate dueDate,
                                    Money total, Money balance, InvoiceStatus status) {
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate,
            total, balance, status);
    }

    public boolean canReceive(Money amount) {
        return !amount.isGreaterThan(balance);
    }

    /**
     * Instante limite: las 00:00 del dia siguiente al vencimiento, hora de Colombia.
     * Equivale a "23:59:59 del dia de vencimiento" sin ambiguedad de fracciones de segundo.
     */
    public boolean isLate(OffsetDateTime paidAt) {
        return !paidAt.toInstant()
            .isBefore(dueDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    /**
     * Descuenta el pago del saldo (RF-07). Un pago que excede el saldo no se aplica ni
     * parcialmente (RF-09). Solo el pago que salda la factura decide entre PAID y PAID_LATE.
     */
    public Invoice applyPayment(Money amount, OffsetDateTime paidAt) {
        if (!canReceive(amount)) {
            throw new IllegalArgumentException(
                "El pago " + amount + " excede el saldo pendiente " + balance);
        }
        Money newBalance = balance.minus(amount);
        InvoiceStatus newStatus;
        if (newBalance.isZero()) {
            newStatus = isLate(paidAt) ? InvoiceStatus.PAID_LATE : InvoiceStatus.PAID;
        } else {
            newStatus = InvoiceStatus.PARTIAL;
        }
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate,
            total, newBalance, newStatus);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return value.strip();
    }

    public String number() { return number; }
    public String supplierNit() { return supplierNit; }
    public String supplierName() { return supplierName; }
    public LocalDate issueDate() { return issueDate; }
    public LocalDate dueDate() { return dueDate; }
    public Money total() { return total; }
    public Money balance() { return balance; }
    public InvoiceStatus status() { return status; }
}