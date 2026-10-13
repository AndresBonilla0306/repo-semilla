package co.conciliacion.backend.application.batch;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(String batchId) { super("No existe el lote " + batchId); }
}