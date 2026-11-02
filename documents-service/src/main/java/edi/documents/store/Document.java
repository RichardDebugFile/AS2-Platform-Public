package edi.documents.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un documento EDI enviado o recibido, con sus metadatos y donde estan sus copias. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "partner_as2_name", nullable = false, length = 128)
    private String partnerAs2Name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(name = "as2_from", length = 128)
    private String as2From;

    @Column(name = "as2_to", length = 128)
    private String as2To;

    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "isa_control", length = 20)
    private String isaControl;

    @Column(name = "gs_control", length = 20)
    private String gsControl;

    @Column(name = "st_control", length = 20)
    private String stControl;

    @Column(length = 255)
    private String mic;

    @Column(name = "mic_algorithm", length = 20)
    private String micAlgorithm;

    @Column(name = "signing_algorithm", length = 20)
    private String signingAlgorithm;

    @Column(name = "encryption_algorithm", length = 20)
    private String encryptionAlgorithm;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status;

    @Column(name = "validation_detail", length = 2000)
    private String validationDetail;

    @Column(length = 4000)
    private String attributes;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "partner_copy_path", length = 1024)
    private String partnerCopyPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_copy_status", nullable = false, length = 20)
    private PartnerCopyStatus partnerCopyStatus;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    public Document(UUID id, String partnerAs2Name, Direction direction, String messageId, Instant createdAt) {
        this.id = id;
        this.partnerAs2Name = partnerAs2Name;
        this.direction = direction;
        this.messageId = messageId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.partnerCopyStatus = PartnerCopyStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public String getPartnerAs2Name() {
        return partnerAs2Name;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getVersion() {
        return version;
    }

    public String getAs2From() {
        return as2From;
    }

    public String getAs2To() {
        return as2To;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getIsaControl() {
        return isaControl;
    }

    public String getGsControl() {
        return gsControl;
    }

    public String getStControl() {
        return stControl;
    }

    public String getMic() {
        return mic;
    }

    public String getMicAlgorithm() {
        return micAlgorithm;
    }

    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getValidationDetail() {
        return validationDetail;
    }

    public String getAttributes() {
        return attributes;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getPartnerCopyPath() {
        return partnerCopyPath;
    }

    public PartnerCopyStatus getPartnerCopyStatus() {
        return partnerCopyStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Lo que se lee del documento X12 (HU-12). */
    public void describe(String typeCode, String version, String isaControl, String gsControl, String stControl) {
        this.typeCode = typeCode;
        this.version = version;
        this.isaControl = blankToNull(isaControl);
        this.gsControl = blankToNull(gsControl);
        this.stControl = blankToNull(stControl);
    }

    /** Lo que trae la transmision AS2. */
    public void transport(String as2From, String as2To, String mic, String micAlgorithm, String signingAlgorithm,
            String encryptionAlgorithm) {
        this.as2From = blankToNull(as2From);
        this.as2To = blankToNull(as2To);
        this.mic = blankToNull(mic);
        this.micAlgorithm = blankToNull(micAlgorithm);
        this.signingAlgorithm = blankToNull(signingAlgorithm);
        this.encryptionAlgorithm = blankToNull(encryptionAlgorithm);
    }

    public void content(long sizeBytes, String sha256, String attributes, String createdBy) {
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.attributes = blankToNull(attributes);
        this.createdBy = createdBy;
    }

    public void status(DocumentStatus status, String validationDetail) {
        this.status = status;
        this.validationDetail = blankToNull(validationDetail);
    }

    public void storedAt(String filePath) {
        this.filePath = filePath;
    }

    public void partnerCopy(String path, PartnerCopyStatus status, Instant at) {
        this.partnerCopyPath = path;
        this.partnerCopyStatus = status;
        this.updatedAt = at;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
