package co.conciliacion.backend.application.port.out;


import co.conciliacion.backend.domain.model.Invoice;
import java.util.Optional;

public interface InvoiceRepository {

    /**
     * Busca la factura bloqueandola para escritura hasta el fin de la transaccion.
     * El nombre anticipa CF-04 (lotes concurrentes sobre la misma factura).
     */
    Optional<Invoice> findByNumberForUpdate(String number);

    void save(Invoice invoice);
}