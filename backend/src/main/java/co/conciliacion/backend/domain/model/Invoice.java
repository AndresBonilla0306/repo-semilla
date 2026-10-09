package co.conciliacion.backend.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** Factura de un proveedor. Inmutable: aplicar un pago devuelve una factura nueva. */
public final class Invoice {

    private final String number;
    private final String supplierNit;
    private final String supplierName;
    private final LocalDate issueDate;
    private final LocalDate dueDate;
    private final Money total;
    private final Money balance;

    private Invoice(String number, String supplierNit, String supplierName,
                    LocalDate issueDate, LocalDate dueDate, Money total, Money balance) {
        this.number = requireText(number, "number");
        this.supplierNit = requireText(supplierNit, "supplierNit");
        this.supplierName = requireText(supplierName, "supplierName");
        this.issueDate = Objects.requireNonNull(issueDate, "issueDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.total = Objects.requireNonNull(total, "total");
        this.balance = Objects.requireNonNull(balance, "balance");
        if (total.isZero()) {
            throw new IllegalArgumentException("El valor total debe ser mayor que cero");
        }
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a la emision");
        }
        if (balance.isGreaterThan(total)) {
            throw new IllegalArgumentException("El saldo no puede superar el valor total");
        }
    }

    /** Factura nueva: el saldo pendiente inicial es igual al total (RF-02). */
    public static Invoice issue(String number, String supplierNit, String supplierName,
                                LocalDate issueDate, LocalDate dueDate, Money total) {
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate, total, total);
    }

    /** Reconstruye una factura ya existente (uso del adaptador de persistencia). */
    public static Invoice rehydrate(String number, String supplierNit, String supplierName,
                                    LocalDate issueDate, LocalDate dueDate, Money total, Money balance) {
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate, total, balance);
    }

    public boolean canReceive(Money amount) {
        return !amount.isGreaterThan(balance);
    }

    /** Descuenta el pago del saldo (RF-07). Un pago que excede el saldo no se aplica ni parcialmente (RF-09). */
    public Invoice applyPayment(Money amount) {
        if (!canReceive(amount)) {
            throw new IllegalArgumentException(
                "El pago " + amount + " excede el saldo pendiente " + balance);
        }
        return new Invoice(number, supplierNit, supplierName, issueDate, dueDate, total, balance.minus(amount));
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
}