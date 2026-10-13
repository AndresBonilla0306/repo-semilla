package co.conciliacion.backend.application.batch;

public class BatchConflictException extends RuntimeException {
    public BatchConflictException(String message) { super(message); }
}