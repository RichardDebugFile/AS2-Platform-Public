package edi.partners.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activar o desactivar produccion. {@code confirm} debe repetir el identificador AS2 del socio: es la
 * autorizacion explicita de quien lo hace.
 */
public record ProductionRequest(@NotNull Boolean enabled, String confirm) {
}
