package edi.documents.query;

import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentStatus;
import edi.documents.store.PartnerCopyStatus;
import java.time.Instant;
import java.util.UUID;

/** Metadatos de un documento para la API (sin el contenido ni las rutas internas). */
public record DocumentView(
        UUID id,
        String partnerAs2Name,
        Direction direction,
        String typeCode,
        String version,
        String as2From,
        String as2To,
        String messageId,
        String isaControl,
        String gsControl,
        String stControl,
        String mic,
        String micAlgorithm,
        String signingAlgorithm,
        String encryptionAlgorithm,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        String validationDetail,
        String attributes,
        String fileName,
        PartnerCopyStatus partnerCopyStatus,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentView of(Document d) {
        String path = d.getFilePath();
        String fileName = path == null ? null : path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        return new DocumentView(d.getId(), d.getPartnerAs2Name(), d.getDirection(), d.getTypeCode(), d.getVersion(),
                d.getAs2From(), d.getAs2To(), d.getMessageId(), d.getIsaControl(), d.getGsControl(), d.getStControl(),
                d.getMic(), d.getMicAlgorithm(), d.getSigningAlgorithm(), d.getEncryptionAlgorithm(), d.getSizeBytes(),
                d.getSha256(), d.getStatus(), d.getValidationDetail(), d.getAttributes(), fileName,
                d.getPartnerCopyStatus(), d.getCreatedBy(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
