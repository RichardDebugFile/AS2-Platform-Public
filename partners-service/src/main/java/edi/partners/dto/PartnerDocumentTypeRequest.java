package edi.partners.dto;

import edi.partners.domain.DocumentDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lista completa de los tipos que intercambia un socio: reemplaza la anterior (HU-13). */
public record PartnerDocumentTypeRequest(@NotNull @Size(max = 100) List<@Valid Item> documentTypes) {

    /**
     * @param extraFields datos adicionales del acuerdo con el socio ({nombre: valor}), p. ej. el
     *                    identificador que el socio usa para la empresa en ese documento
     */
    public record Item(@NotNull UUID documentTypeId, @NotNull DocumentDirection direction,
            @Size(max = 500) String notes, @Size(max = 20) Map<String, String> extraFields) {
    }
}
