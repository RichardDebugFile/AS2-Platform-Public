package edi.partners.dto;

import edi.partners.domain.KeyUsage;
import edi.partners.domain.PartnerCertificate;
import java.time.Instant;
import java.util.UUID;

/** Lo que el motor AS2 necesita de un certificado: su uso, si es el preferente, vigencia y el PEM. */
public record InternalCertificateView(UUID id, UUID partnerId, String partnerAs2Name, KeyUsage keyUsage,
                                      boolean preferred, String fingerprintSha256, Instant validFrom,
                                      Instant validTo, String pem) {

    public static InternalCertificateView of(PartnerCertificate c) {
        return new InternalCertificateView(c.getId(), c.getPartner().getId(), c.getPartner().getAs2Name(),
                c.getKeyUsage(), c.isPreferred(), c.getFingerprintSha256(), c.getValidFrom(), c.getValidTo(),
                c.getCertPem());
    }
}
