package edi.partners.dto;

import edi.partners.domain.ArchiveMode;
import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MimeType;
import edi.partners.domain.OutgoingConfig;
import edi.partners.domain.SendingMode;
import edi.partners.domain.SigningAlgorithm;
import java.time.Instant;
import java.util.UUID;

public record OutgoingConfigResponse(
        UUID id,
        UUID partnerId,
        String localFolder,
        SendingMode sendingMode,
        String filenameFilter,
        EncryptionAlgorithm encryptionAlgorithm,
        SigningAlgorithm signingAlgorithm,
        boolean compression,
        MimeType mimeType,
        ArchiveMode archiveMode,
        String archiveFolder,
        String updatedBy,
        Instant updatedAt) {

    public static OutgoingConfigResponse of(OutgoingConfig c) {
        return new OutgoingConfigResponse(c.getId(), c.getPartner().getId(), c.getLocalFolder(),
                c.getSendingMode(), c.getFilenameFilter(), c.getEncryptionAlgorithm(), c.getSigningAlgorithm(),
                c.isCompression(), c.getMimeType(), c.getArchiveMode(), c.getArchiveFolder(), c.getUpdatedBy(),
                c.getUpdatedAt());
    }
}
