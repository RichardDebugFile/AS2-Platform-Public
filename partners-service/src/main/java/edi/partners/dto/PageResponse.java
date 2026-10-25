package edi.partners.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Pagina de resultados con un formato estable (el mismo que usa auth-service). */
public record PageResponse<T>(List<T> content, int number, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
