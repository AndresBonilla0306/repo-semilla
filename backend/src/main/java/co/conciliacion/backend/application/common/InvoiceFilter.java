package co.conciliacion.backend.application.common;

import co.conciliacion.backend.domain.model.InvoiceStatus;
import co.conciliacion.backend.domain.model.Money;
import java.time.LocalDate;

/** Todos los campos son opcionales; los presentes se combinan con AND. */
public record InvoiceFilter(String nit, InvoiceStatus status, LocalDate dueFrom, LocalDate dueTo,
                            Money balanceMin, Money balanceMax) { }