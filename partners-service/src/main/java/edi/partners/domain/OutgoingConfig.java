package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Salida: carpeta que vigila el motor AS2, que archivos envia y que hace con ellos despues. */
@Entity
@Table(name = "outgoing_config")
public class OutgoingConfig extends PartnerConfig {

    @Column(name = "local_folder", nullable = false, length = 512)
    private String localFolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "sending_mode", nullable = false, length = 16)
    private SendingMode sendingMode;

    @Column(name = "filename_filter")
    private String filenameFilter;

    @Enumerated(EnumType.STRING)
    @Column(name = "encryption_algorithm", length = 16)
    private EncryptionAlgorithm encryptionAlgorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "signing_algorithm", length = 16)
    private SigningAlgorithm signingAlgorithm;

    @Column(name = "compression", nullable = false)
    private boolean compression;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", nullable = false, length = 32)
    private MimeType mimeType = MimeType.APPLICATION_EDI_X12;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_mode", nullable = false, length = 8)
    private ArchiveMode archiveMode = ArchiveMode.MOVE;

    @Column(name = "archive_folder", length = 512)
    private String archiveFolder;

    protected OutgoingConfig() {
    }

    public OutgoingConfig(Partner partner) {
        super(partner);
    }

    public String getLocalFolder() {
        return localFolder;
    }

    public void setLocalFolder(String localFolder) {
        this.localFolder = localFolder;
    }

    public SendingMode getSendingMode() {
        return sendingMode;
    }

    public void setSendingMode(SendingMode sendingMode) {
        this.sendingMode = sendingMode;
    }

    public String getFilenameFilter() {
        return filenameFilter;
    }

    public void setFilenameFilter(String filenameFilter) {
        this.filenameFilter = filenameFilter;
    }

    public EncryptionAlgorithm getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(EncryptionAlgorithm encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public SigningAlgorithm getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public void setSigningAlgorithm(SigningAlgorithm signingAlgorithm) {
        this.signingAlgorithm = signingAlgorithm;
    }

    public boolean isCompression() {
        return compression;
    }

    public void setCompression(boolean compression) {
        this.compression = compression;
    }

    public MimeType getMimeType() {
        return mimeType;
    }

    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }

    public ArchiveMode getArchiveMode() {
        return archiveMode;
    }

    public void setArchiveMode(ArchiveMode archiveMode) {
        this.archiveMode = archiveMode;
    }

    public String getArchiveFolder() {
        return archiveFolder;
    }

    public void setArchiveFolder(String archiveFolder) {
        this.archiveFolder = archiveFolder;
    }
}
