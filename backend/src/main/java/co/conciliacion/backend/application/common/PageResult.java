package co.conciliacion.backend.application.common;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}