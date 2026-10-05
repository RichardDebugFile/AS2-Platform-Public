package edi.auth.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagina de resultados con forma fija. Serializar {@code Page} de Spring Data tal cual expone su
 * estructura interna, que puede cambiar entre versiones; la consola depende de estos cinco campos.
 *
 * @param number pagina actual, empezando en 0
 */
public record PageResponse<T>(List<T> content, int number, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
