package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Tipo de documento del catalogo EDI (HU-13), unico por estandar, codigo y version. */
@Entity
@Table(name = "document_type")
@EntityListeners(AuditingEntityListener.class)
public class DocumentType {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DocumentStandard standard;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** Documentos que el socio deberia devolver como respuesta a este. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "document_type_response",
            joinColumns = @JoinColumn(name = "document_type_id"),
            inverseJoinColumns = @JoinColumn(name = "response_type_id"))
    private Set<DocumentType> responses = new LinkedHashSet<>();

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
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

    protected DocumentType() {
    }

    public DocumentType(DocumentStandard standard, String code, String version, String name) {
        this.standard = standard;
        this.code = code;
        this.version = version;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public DocumentStandard getStandard() {
        return standard;
    }

    public void setStandard(DocumentStandard standard) {
        this.standard = standard;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<DocumentType> getResponses() {
        return responses;
    }

    /** "X12 850 4010": como se nombra el tipo en mensajes y en la bitacora. */
    public String label() {
        return standard + " " + code + " " + version;
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
