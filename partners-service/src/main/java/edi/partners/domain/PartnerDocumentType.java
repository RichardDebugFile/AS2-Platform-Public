package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Tipo de documento que intercambia un socio, en una direccion, con sus datos adicionales (HU-13). */
@Entity
@Table(name = "partner_document_type")
public class PartnerDocumentType {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // igual que V6: se borra con el socio
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentDirection direction;

    @Column(length = 500)
    private String notes;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "extra_fields", columnDefinition = "text")
    private Map<String, String> extraFields = new LinkedHashMap<>();

    protected PartnerDocumentType() {
    }

    public PartnerDocumentType(Partner partner, DocumentType documentType, DocumentDirection direction, String notes,
            Map<String, String> extraFields) {
        this.partner = partner;
        this.documentType = documentType;
        this.direction = direction;
        this.notes = notes;
        this.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
    }

    public UUID getId() {
        return id;
    }

    public Partner getPartner() {
        return partner;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public DocumentDirection getDirection() {
        return direction;
    }

    public String getNotes() {
        return notes;
    }

    public Map<String, String> getExtraFields() {
        return extraFields;
    }
}
