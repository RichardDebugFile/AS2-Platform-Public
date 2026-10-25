package edi.partners.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Alta en un paso: el socio y, opcionalmente, sus tres configuraciones. La que no venga se crea con
 * los valores por defecto (carpetas bajo la raiz de almacenamiento del socio).
 */
public record CompletePartnerRequest(
        @NotNull @Valid PartnerRequest partner,
        @Valid OutgoingConfigRequest outgoing,
        @Valid IncomingConfigRequest incoming,
        @Valid MdnConfigRequest mdn) {
}
