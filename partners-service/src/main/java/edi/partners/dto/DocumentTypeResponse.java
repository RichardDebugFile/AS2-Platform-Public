package edi.partners.dto;

import edi.partners.domain.DocumentStandard;
import edi.partners.domain.DocumentType;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tipo del catalogo con sus respuestas esperadas y cuantos socios lo usan.
 *
 * @param partnerCount socios que lo tienen asignado; con alguno no se puede borrar
 */
public record DocumentTypeResponse(UUID id, DocumentStandard standard, String code, String version, String name,
        String description, List<TypeRef> responses, long partnerCount) {

    /** Referencia breve a otro tipo del catalogo. */
    public record TypeRef(UUID id, DocumentStandard standard, String code, String version, String name) {

        public static TypeRef of(DocumentType t) {
            return new TypeRef(t.getId(), t.getStandard(), t.getCode(), t.getVersion(), t.getName());
        }
    }

    public static DocumentTypeResponse of(DocumentType t, long partnerCount) {
        List<TypeRef> responses = t.getResponses().stream()
                .sorted(Comparator.comparing(DocumentType::getCode).thenComparing(DocumentType::getVersion))
                .map(TypeRef::of)
                .toList();
        return new DocumentTypeResponse(t.getId(), t.getStandard(), t.getCode(), t.getVersion(), t.getName(),
                t.getDescription(), responses, partnerCount);
    }
}
