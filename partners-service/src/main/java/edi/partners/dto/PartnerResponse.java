package edi.partners.dto;

import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MicAlgorithm;
import edi.partners.domain.Partner;
import edi.partners.domain.PartnerType;
import edi.partners.domain.SigningAlgorithm;
import edi.partners.domain.TransportType;
import java.time.Instant;
import java.util.UUID;

/** Socio tal como lo ve la consola. La clave basica nunca sale: solo si existe. */
public record PartnerResponse(
        UUID id,
        String as2Name,
        String partnerName,
        String partnerAs2Name,
        PartnerType partnerType,
        String externalId,
        String description,
        String serverAddress,
        int port,
        TransportType transportType,
        String path,
        String endpointUrl,
        String basicAuthUsername,
        boolean hasBasicAuthPassword,
        Integer connectionTimeoutSeconds,
        Integer readTimeoutSeconds,
        Integer maxRetryAttempts,
        Integer retryBackoffMs,
        SigningAlgorithm signingAlgorithm,
        EncryptionAlgorithm encryptionAlgorithm,
        MicAlgorithm micAlgorithm,
        boolean compressionEnabled,
        boolean requestMdn,
        boolean requestSignedMdn,
        MdnMode mdnMode,
        String mdnReceiptUrl,
        boolean validateCertificate,
        boolean validateSignature,
        boolean validateMic,
        boolean enableDualStorage,
        boolean enableIncomingQueue,
        String sharedFolderName,
        String dataLocation,
        String contactName,
        String contactPhone,
        String contactEmail,
        String tradingPartnerEmail,
        boolean alertOnFailure,
        boolean active,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt) {

    public static PartnerResponse of(Partner p) {
        return new PartnerResponse(p.getId(), p.getAs2Name(), p.getPartnerName(), p.getPartnerAs2Name(),
                p.getPartnerType(), p.getExternalId(), p.getDescription(), p.getServerAddress(), p.getPort(),
                p.getTransportType(), p.getPath(), p.endpointUrl(), p.getBasicAuthUsername(),
                p.getBasicAuthPasswordEnc() != null, p.getConnectionTimeoutSeconds(), p.getReadTimeoutSeconds(),
                p.getMaxRetryAttempts(), p.getRetryBackoffMs(), p.getSigningAlgorithm(),
                p.getEncryptionAlgorithm(), p.getMicAlgorithm(), p.isCompressionEnabled(), p.isRequestMdn(),
                p.isRequestSignedMdn(), p.getMdnMode(), p.getMdnReceiptUrl(), p.isValidateCertificate(),
                p.isValidateSignature(), p.isValidateMic(), p.isEnableDualStorage(), p.isEnableIncomingQueue(),
                p.getSharedFolderName(), p.getDataLocation(), p.getContactName(), p.getContactPhone(),
                p.getContactEmail(), p.getTradingPartnerEmail(), p.isAlertOnFailure(), p.isActive(),
                p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
    }
}
