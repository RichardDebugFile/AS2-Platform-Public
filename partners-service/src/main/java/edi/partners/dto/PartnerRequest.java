package edi.partners.dto;

import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MicAlgorithm;
import edi.partners.domain.PartnerType;
import edi.partners.domain.SigningAlgorithm;
import edi.partners.domain.TransportType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Alta o edicion de un socio. Los campos opcionales en {@code null} toman el valor por defecto en el
 * alta y conservan el actual en la edicion. {@code basicAuthPassword} solo se escribe: vacio en una
 * edicion conserva la clave guardada.
 */
public record PartnerRequest(
        // Identidad
        @NotBlank @Size(max = 128) String as2Name,
        @Size(max = 255) String partnerName,
        @Size(max = 128) String partnerAs2Name,
        PartnerType partnerType,
        @Size(max = 128) String externalId,
        @Size(max = 2000) String description,
        // Conexion
        @NotBlank @Size(max = 255) String serverAddress,
        @NotNull @Min(1) @Max(65535) Integer port,
        TransportType transportType,
        @Size(max = 255) String path,
        @Size(max = 255) String basicAuthUsername,
        @Size(max = 255) String basicAuthPassword,
        @Min(1) @Max(3600) Integer connectionTimeoutSeconds,
        @Min(1) @Max(3600) Integer readTimeoutSeconds,
        @PositiveOrZero @Max(20) Integer maxRetryAttempts,
        @PositiveOrZero @Max(3_600_000) Integer retryBackoffMs,
        // Seguridad AS2
        SigningAlgorithm signingAlgorithm,
        EncryptionAlgorithm encryptionAlgorithm,
        MicAlgorithm micAlgorithm,
        Boolean compressionEnabled,
        Boolean requestMdn,
        Boolean requestSignedMdn,
        MdnMode mdnMode,
        @Size(max = 1024) String mdnReceiptUrl,
        Boolean validateCertificate,
        Boolean validateSignature,
        Boolean validateMic,
        // Almacenamiento
        Boolean enableDualStorage,
        Boolean enableIncomingQueue,
        @Size(max = 255) String sharedFolderName,
        @Size(max = 1024) String dataLocation,
        // Contacto
        @Size(max = 255) String contactName,
        @Size(max = 50) String contactPhone,
        @Email @Size(max = 255) String contactEmail,
        @Email @Size(max = 255) String tradingPartnerEmail,
        Boolean alertOnFailure) {
}
