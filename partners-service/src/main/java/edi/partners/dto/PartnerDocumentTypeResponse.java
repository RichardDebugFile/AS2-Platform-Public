package edi.partners.dto;

import edi.partners.domain.DocumentDirection;
import edi.partners.domain.PartnerDocumentType;
import edi.partners.dto.DocumentTypeResponse.TypeRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tipo que intercambia un socio, con su direccion, sus datos adicionales y la respuesta esperada. */
public record PartnerDocumentTypeResponse(UUID id, TypeRef documentType, DocumentDirection direction, String notes,
        Map<String, String> extraFields, List<TypeRef> expectedResponses) {

    public static PartnerDocumentTypeResponse of(PartnerDocumentType a) {
        DocumentTypeResponse type = DocumentTypeResponse.of(a.getDocumentType(), 0);
        return new PartnerDocumentTypeResponse(a.getId(), TypeRef.of(a.getDocumentType()), a.getDirection(),
                a.getNotes(), Map.copyOf(a.getExtraFields()), type.responses());
    }
}
