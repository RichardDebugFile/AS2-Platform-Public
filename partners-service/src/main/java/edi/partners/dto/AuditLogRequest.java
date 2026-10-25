package edi.partners.dto;

import edi.partners.domain.AuditStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * Evento que registra otro servicio (p. ej. el motor AS2: {@code DOCUMENT_SENT}, {@code MDN_RECEIVED}).
 * El socio se indica por id o por identificador AS2; sin usuario, queda "system".
 */
public record AuditLogRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "debe ir en MAYUSCULAS_CON_GUIONES_BAJOS")
        String eventType,
        @NotNull AuditStatus status,
        @NotBlank @Size(max = 32) String source,
        @Size(max = 64) String actor,
        UUID partnerId,
        @Size(max = 128) String partnerAs2Name,
        @Size(max = 255) String messageId,
        @Size(max = 2000) String detail,
        Map<String, ChangeValue> changes) {

    /** Valor anterior y nuevo de un campo. */
    public record ChangeValue(Object from, Object to) {
    }
}
