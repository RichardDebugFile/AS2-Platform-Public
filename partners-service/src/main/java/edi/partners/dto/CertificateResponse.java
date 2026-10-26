package edi.partners.dto;

import edi.partners.domain.KeyUsage;
import edi.partners.domain.PartnerCertificate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Certificado de un socio tal como lo muestra la consola.
 *
 * @param validity      {@code VALID}, {@code EXPIRING} (vence en menos de 30 dias), {@code EXPIRED} o
 *                      {@code NOT_YET_VALID} (un reemplazo anunciado que aun no empieza)
 * @param daysToExpire  dias hasta {@code validTo} (negativo si ya vencio)
 * @param pem           solo en el detalle; en las listas va nulo
 */
public record CertificateResponse(UUID id, UUID partnerId, KeyUsage keyUsage, String issuedTo, String issuedBy,
                                  String subjectDn, String issuerDn, String serialNumber, String fingerprintSha256,
                                  Instant validFrom, Instant validTo, Validity validity, long daysToExpire,
                                  boolean active, boolean preferred, String description, String createdBy,
                                  Instant createdAt, String updatedBy, Instant updatedAt, String pem) {

    /** Umbral del aviso de vencimiento en la consola. */
    public static final Duration EXPIRING_WITHIN = Duration.ofDays(30);

    public enum Validity { VALID, EXPIRING, EXPIRED, NOT_YET_VALID }

    public static CertificateResponse of(PartnerCertificate c, Instant now, boolean withPem) {
        return new CertificateResponse(c.getId(), c.getPartner().getId(), c.getKeyUsage(), c.getIssuedTo(),
                c.getIssuedBy(), c.getSubjectDn(), c.getIssuerDn(), c.getSerialNumber(), c.getFingerprintSha256(),
                c.getValidFrom(), c.getValidTo(), validity(c, now), Duration.between(now, c.getValidTo()).toDays(),
                c.isActive(), c.isPreferred(), c.getDescription(), c.getCreatedBy(), c.getCreatedAt(),
                c.getUpdatedBy(), c.getUpdatedAt(), withPem ? c.getCertPem() : null);
    }

    public static Validity validity(PartnerCertificate c, Instant now) {
        if (c.isExpired(now)) {
            return Validity.EXPIRED;
        }
        if (c.getValidFrom().isAfter(now)) {
            return Validity.NOT_YET_VALID;
        }
        return c.getValidTo().isBefore(now.plus(EXPIRING_WITHIN)) ? Validity.EXPIRING : Validity.VALID;
    }
}
