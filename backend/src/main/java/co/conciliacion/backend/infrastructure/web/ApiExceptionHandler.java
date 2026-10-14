package co.conciliacion.backend.infrastructure.web;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.conciliacion.backend.application.batch.BatchConflictException;
import co.conciliacion.backend.application.batch.BatchNotFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String error, String detail) { }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("INVALID_REQUEST", detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("INVALID_REQUEST", "Cuerpo de la solicitud ilegible o con formato invalido"));
    }

    /** Reglas del dominio violadas por datos de entrada (por ejemplo, pago en cero). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> domainRule(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(BatchConflictException.class)
    public ResponseEntity<ApiError> batchConflict(BatchConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("BATCH_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ApiError> batchNotFound(BatchNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("BATCH_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badParam(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("INVALID_REQUEST", "Parametro invalido: " + e.getName()));
    }
}