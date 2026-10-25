package edi.partners.dto;

import edi.partners.domain.ArchiveMode;
import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MimeType;
import edi.partners.domain.SendingMode;
import edi.partners.domain.SigningAlgorithm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OutgoingConfigRequest(
        @NotBlank @Size(max = 512) String localFolder,
        @NotNull SendingMode sendingMode,
        @Size(max = 255) String filenameFilter,
        EncryptionAlgorithm encryptionAlgorithm,
        SigningAlgorithm signingAlgorithm,
        Boolean compression,
        MimeType mimeType,
        ArchiveMode archiveMode,
        @Size(max = 512) String archiveFolder) {
}
