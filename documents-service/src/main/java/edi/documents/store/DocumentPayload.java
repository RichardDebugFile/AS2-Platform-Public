package edi.documents.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Contenido del documento tal como llego (1:1 con {@link Document}). */
@Entity
@Table(name = "document_payloads")
public class DocumentPayload {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false)
    private byte[] content;

    protected DocumentPayload() {
    }

    public DocumentPayload(UUID documentId, byte[] content) {
        this.documentId = documentId;
        this.content = content.clone();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
