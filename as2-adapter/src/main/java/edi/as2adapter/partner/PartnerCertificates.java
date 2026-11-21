package edi.as2adapter.partner;

import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.partner.PartnerDirectory.Certificate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PartnerCertificates {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerCertificates.class);
    private static final Comparator<Certificate> PREFERRED_FIRST = Comparator
            .comparing(Certificate::preferred).reversed()
            .thenComparing(Certificate::validTo, Comparator.reverseOrder());

    private final int maxRecipients;
    private final boolean encryptToAll;

    public PartnerCertificates(@Value("${as2.outbound.max-recipients:3}") int maxRecipients,
                               @Value("${as2.outbound.encrypt-to-all-active:true}") boolean encryptToAll) {
        if (maxRecipients < 1) throw new IllegalArgumentException("as2.outbound.max-recipients debe ser positivo");
        this.maxRecipients = maxRecipients;
        this.encryptToAll = encryptToAll;
    }

    public List<X509Certificate> recipients(List<Certificate> certificates, Instant now) {
        List<Certificate> eligible = certificates.stream()
                .filter(c -> c.pem() != null && !c.pem().isBlank() && !c.validFrom().isAfter(now))
                .sorted(PREFERRED_FIRST).toList();
        List<Certificate> valid = eligible.stream().filter(c -> !c.validTo().isBefore(now)).toList();
        if (valid.isEmpty() && !eligible.isEmpty()) {
            LOG.warn("Los certificados de cifrado activos están caducados. Se usa el preferente hasta su renovación.");
            return List.of(parse(eligible.getFirst()));
        }
        return valid.stream().limit(encryptToAll ? maxRecipients : 1).map(PartnerCertificates::parse).toList();
    }

    public static List<X509Certificate> current(List<Certificate> certificates, Instant now) {
        return certificates.stream().filter(c -> !c.validFrom().isAfter(now) && !c.validTo().isBefore(now))
                .map(PartnerCertificates::parse).toList();
    }

    private static X509Certificate parse(Certificate certificate) {
        try {
            var input = new ByteArrayInputStream(certificate.pem().getBytes(StandardCharsets.US_ASCII));
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        } catch (CertificateException e) {
            throw new As2Exception("No se pudo leer un certificado activo del socio", e);
        }
    }
}
