package edi.documents.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Acuse MDN de un documento (1:1). Lo registran el envio y la recepcion AS2 (HU-15 y HU-16). */
@Entity
@Table(name = "document_mdn")
public class DocumentMdn {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 255)
    private String disposition;

    @Column(name = "received_mic", length = 255)
    private String receivedMic;

    @Column(name = "mic_matches")
    private Boolean micMatches;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "raw_mdn", length = 20000)
    private String rawMdn;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected DocumentMdn() {
    }

    public DocumentMdn(UUID documentId, String disposition, String receivedMic, Boolean micMatches,
            Boolean signatureValid, String rawMdn, Instant receivedAt) {
        this.documentId = documentId;
        this.disposition = disposition;
        this.receivedMic = receivedMic;
        this.micMatches = micMatches;
        this.signatureValid = signatureValid;
        this.rawMdn = rawMdn;
        this.receivedAt = receivedAt;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getDisposition() {
        return disposition;
    }

    public String getReceivedMic() {
        return receivedMic;
    }

    public Boolean getMicMatches() {
        return micMatches;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public String getRawMdn() {
        return rawMdn;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
