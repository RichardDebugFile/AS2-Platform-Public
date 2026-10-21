package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Socio comercial AS2. El identificador {@code as2Name} es el {@code AS2-From} con el que el socio
 * firma sus mensajes y da nombre a sus carpetas; es unico entre los socios activos (la baja es
 * logica). La clave de autenticacion basica se guarda cifrada y nunca sale en las respuestas.
 */
@Entity
@Table(name = "partner")
@EntityListeners(AuditingEntityListener.class)
public class Partner {

    @Id
    private UUID id;
    @Column(name = "as2_name", nullable = false, length = 128)
    private String as2Name;
    @Column(name = "partner_name")
    private String partnerName;
    @Column(name = "partner_as2_name", length = 128)
    private String partnerAs2Name;
    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", length = 32)
    private PartnerType partnerType;
    @Column(name = "external_id", length = 128)
    private String externalId;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Column(name = "server_address", nullable = false)
    private String serverAddress;
    @Column(name = "port", nullable = false)
    private int port;
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 8)
    private TransportType transportType;
    @Column(name = "path")
    private String path;
    @Column(name = "basic_auth_username")
    private String basicAuthUsername;
    @Column(name = "basic_auth_password_enc", length = 1024)
    private String basicAuthPasswordEnc;
    @Column(name = "connection_timeout_seconds")
    private Integer connectionTimeoutSeconds;
    @Column(name = "read_timeout_seconds")
    private Integer readTimeoutSeconds;
    @Column(name = "max_retry_attempts")
    private Integer maxRetryAttempts;
    @Column(name = "retry_backoff_ms")
    private Integer retryBackoffMs;
    @Enumerated(EnumType.STRING)
    @Column(name = "signing_algorithm", length = 16)
    private SigningAlgorithm signingAlgorithm;
    @Enumerated(EnumType.STRING)
    @Column(name = "encryption_algorithm", length = 16)
    private EncryptionAlgorithm encryptionAlgorithm;
    @Enumerated(EnumType.STRING)
    @Column(name = "mic_algorithm", length = 16)
    private MicAlgorithm micAlgorithm;
    @Column(name = "compression_enabled", nullable = false)
    private boolean compressionEnabled = false;
    @Column(name = "request_mdn", nullable = false)
    private boolean requestMdn = true;
    @Column(name = "request_signed_mdn", nullable = false)
    private boolean requestSignedMdn = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "mdn_mode", nullable = false, length = 8)
    private MdnMode mdnMode = MdnMode.SYNC;
    @Column(name = "mdn_receipt_url", length = 1024)
    private String mdnReceiptUrl;
    @Column(name = "validate_certificate", nullable = false)
    private boolean validateCertificate = true;
    @Column(name = "validate_signature", nullable = false)
    private boolean validateSignature = true;
    @Column(name = "validate_mic", nullable = false)
    private boolean validateMic = true;
    @Column(name = "enable_dual_storage", nullable = false)
    private boolean enableDualStorage = true;
    @Column(name = "enable_incoming_queue", nullable = false)
    private boolean enableIncomingQueue = false;
    @Column(name = "shared_folder_name")
    private String sharedFolderName;
    @Column(name = "data_location", length = 1024)
    private String dataLocation;
    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_phone", length = 50)
    private String contactPhone;
    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "trading_partner_email")
    private String tradingPartnerEmail;
    @Column(name = "alert_on_failure", nullable = false)
    private boolean alertOnFailure = true;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 64)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Partner() {
        this.id = UUID.randomUUID();
    }

    /** URL del servidor AS2 del socio: protocolo, host, puerto y ruta. */
    public String endpointUrl() {
        String p = path == null || path.isBlank() ? "" : (path.startsWith("/") ? path : "/" + path);
        return transportType.scheme() + "://" + serverAddress + ":" + port + p;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAs2Name() {
        return as2Name;
    }

    public void setAs2Name(String as2Name) {
        this.as2Name = as2Name;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public String getPartnerAs2Name() {
        return partnerAs2Name;
    }

    public void setPartnerAs2Name(String partnerAs2Name) {
        this.partnerAs2Name = partnerAs2Name;
    }

    public PartnerType getPartnerType() {
        return partnerType;
    }

    public void setPartnerType(PartnerType partnerType) {
        this.partnerType = partnerType;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }

    public void setBasicAuthUsername(String basicAuthUsername) {
        this.basicAuthUsername = basicAuthUsername;
    }

    public String getBasicAuthPasswordEnc() {
        return basicAuthPasswordEnc;
    }

    public void setBasicAuthPasswordEnc(String basicAuthPasswordEnc) {
        this.basicAuthPasswordEnc = basicAuthPasswordEnc;
    }

    public Integer getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(Integer connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public Integer getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(Integer readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public Integer getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(Integer maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public Integer getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(Integer retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public SigningAlgorithm getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public void setSigningAlgorithm(SigningAlgorithm signingAlgorithm) {
        this.signingAlgorithm = signingAlgorithm;
    }

    public EncryptionAlgorithm getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(EncryptionAlgorithm encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public MicAlgorithm getMicAlgorithm() {
        return micAlgorithm;
    }

    public void setMicAlgorithm(MicAlgorithm micAlgorithm) {
        this.micAlgorithm = micAlgorithm;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public boolean isRequestMdn() {
        return requestMdn;
    }

    public void setRequestMdn(boolean requestMdn) {
        this.requestMdn = requestMdn;
    }

    public boolean isRequestSignedMdn() {
        return requestSignedMdn;
    }

    public void setRequestSignedMdn(boolean requestSignedMdn) {
        this.requestSignedMdn = requestSignedMdn;
    }

    public MdnMode getMdnMode() {
        return mdnMode;
    }

    public void setMdnMode(MdnMode mdnMode) {
        this.mdnMode = mdnMode;
    }

    public String getMdnReceiptUrl() {
        return mdnReceiptUrl;
    }

    public void setMdnReceiptUrl(String mdnReceiptUrl) {
        this.mdnReceiptUrl = mdnReceiptUrl;
    }

    public boolean isValidateCertificate() {
        return validateCertificate;
    }

    public void setValidateCertificate(boolean validateCertificate) {
        this.validateCertificate = validateCertificate;
    }

    public boolean isValidateSignature() {
        return validateSignature;
    }

    public void setValidateSignature(boolean validateSignature) {
        this.validateSignature = validateSignature;
    }

    public boolean isValidateMic() {
        return validateMic;
    }

    public void setValidateMic(boolean validateMic) {
        this.validateMic = validateMic;
    }

    public boolean isEnableDualStorage() {
        return enableDualStorage;
    }

    public void setEnableDualStorage(boolean enableDualStorage) {
        this.enableDualStorage = enableDualStorage;
    }

    public boolean isEnableIncomingQueue() {
        return enableIncomingQueue;
    }

    public void setEnableIncomingQueue(boolean enableIncomingQueue) {
        this.enableIncomingQueue = enableIncomingQueue;
    }

    public String getSharedFolderName() {
        return sharedFolderName;
    }

    public void setSharedFolderName(String sharedFolderName) {
        this.sharedFolderName = sharedFolderName;
    }

    public String getDataLocation() {
        return dataLocation;
    }

    public void setDataLocation(String dataLocation) {
        this.dataLocation = dataLocation;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getTradingPartnerEmail() {
        return tradingPartnerEmail;
    }

    public void setTradingPartnerEmail(String tradingPartnerEmail) {
        this.tradingPartnerEmail = tradingPartnerEmail;
    }

    public boolean isAlertOnFailure() {
        return alertOnFailure;
    }

    public void setAlertOnFailure(boolean alertOnFailure) {
        this.alertOnFailure = alertOnFailure;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
