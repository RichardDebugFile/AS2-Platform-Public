package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Certificado X.509 de un socio (HU-11), solo la parte publica. Un socio puede tener varios
 * certificados activos del mismo uso (doble compuerta durante una rotacion); como maximo uno es el
 * preferente. Los datos descriptivos se extraen del certificado al cargarlo y no se editan.
 */
@Entity
@Table(name = "partner_certificate")
@EntityListeners(AuditingEntityListener.class)
public class PartnerCertificate {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // igual que V5: ON DELETE CASCADE
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_usage", nullable = false, updatable = false, length = 16)
    private KeyUsage keyUsage;

    @Column(name = "issued_to", nullable = false, updatable = false)
    private String issuedTo;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private String issuedBy;

    @Column(name = "subject_dn", nullable = false, updatable = false, length = 1024)
    private String subjectDn;

    @Column(name = "issuer_dn", nullable = false, updatable = false, length = 1024)
    private String issuerDn;

    @Column(name = "serial_number", nullable = false, updatable = false, length = 128)
    private String serialNumber;

    @Column(name = "fingerprint_sha256", nullable = false, updatable = false, length = 64)
    private String fingerprintSha256;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false, updatable = false)
    private Instant validTo;

    @Column(name = "cert_pem", nullable = false, updatable = false, columnDefinition = "text")
    private String certPem;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "preferred", nullable = false)
    private boolean preferred;

    @Column(name = "description")
    private String description;

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

    protected PartnerCertificate() {
    }

    public PartnerCertificate(Partner partner, KeyUsage keyUsage, CertificateData data) {
        this.partner = partner;
        this.keyUsage = keyUsage;
        this.issuedTo = data.issuedTo();
        this.issuedBy = data.issuedBy();
        this.subjectDn = data.subjectDn();
        this.issuerDn = data.issuerDn();
        this.serialNumber = data.serialNumber();
        this.fingerprintSha256 = data.fingerprintSha256();
        this.validFrom = data.validFrom();
        this.validTo = data.validTo();
        this.certPem = data.pem();
    }

    /** Datos extraidos del certificado al cargarlo. */
    public record CertificateData(String issuedTo, String issuedBy, String subjectDn, String issuerDn,
                                  String serialNumber, String fingerprintSha256, Instant validFrom, Instant validTo,
                                  String pem) {
    }

    public boolean isExpired(Instant now) {
        return !validTo.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public Partner getPartner() {
        return partner;
    }

    public KeyUsage getKeyUsage() {
        return keyUsage;
    }

    public String getIssuedTo() {
        return issuedTo;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public String getIssuerDn() {
        return issuerDn;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public String getCertPem() {
        return certPem;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            this.preferred = false;
        }
    }

    public boolean isPreferred() {
        return preferred;
    }

    public void setPreferred(boolean preferred) {
        this.preferred = preferred;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
