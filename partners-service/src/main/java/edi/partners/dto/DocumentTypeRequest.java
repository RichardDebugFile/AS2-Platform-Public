package edi.partners.dto;

import edi.partners.domain.DocumentStandard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Alta o cambio de un tipo del catalogo (HU-13). {@code responseTypeIds} son los tipos que se esperan
 * como respuesta (850 -> 855, 856, 810); nulo deja los que tenia.
 */
public record DocumentTypeRequest(
        @NotNull DocumentStandard standard,
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9]+", message = "solo letras y numeros") String code,
        @NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9.]+", message = "solo letras, numeros y puntos")
        String version,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        List<UUID> responseTypeIds) {
}
