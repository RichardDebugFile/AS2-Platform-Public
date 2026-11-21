package edi.as2adapter.outbound;

import edi.as2adapter.as2.As2Client;
import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.LocalIdentity;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.as2.OutboundMessage;
import edi.as2adapter.as2.SendResult;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.audit.AuditEvent;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.partner.PartnerCertificates;
import edi.as2adapter.partner.PartnerDirectory;
import edi.as2adapter.partner.PartnerDirectory.Agreement;
import edi.as2adapter.partner.PartnerTls;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboundService {

    public static final String X12 = "application/edi-x12";
    private static final String FAILED = "SEND_FAILED";
    private final PartnerDirectory partners;
    private final PartnerCertificates certificates;
    private final PartnerTls tls;
    private final LocalIdentity identity;
    private final As2Documents documents;
    private final AuditClient audit;
    private final MessageBuilder builder = new MessageBuilder(Clock.systemUTC());

    public OutboundService(PartnerDirectory partners, PartnerCertificates certificates, PartnerTls tls,
                           LocalIdentity identity, As2Documents documents, AuditClient audit) {
        this.partners = partners;
        this.certificates = certificates;
        this.tls = tls;
        this.identity = identity;
        this.documents = documents;
        this.audit = audit;
    }

    public Outcome send(String as2Name, byte[] content, String fileName, String user) {
        Agreement agreement = partners.find(as2Name);
        if (agreement == null) throw new IllegalArgumentException("Socio AS2 activo no encontrado");
        return send(agreement, content, fileName, user);
    }

    public Outcome send(Agreement agreement, byte[] content, String fileName, String user) {
        var partner = agreement.partner();
        URI target = endpoint(partner.endpointUrl());
        var outgoing = agreement.outgoing();
        if (outgoing != null && outgoing.compression()) {
            throw new IllegalArgumentException("La compresión AS2 del socio requiere una configuración sin compresión");
        }
        var mdn = agreement.mdn();
        boolean requestMdn = partner.requestMdn();
        if (requestMdn && "ASYNC".equals(mdn == null ? partner.mdnMode() : mdn.deliveryMode())) {
            throw new IllegalArgumentException("El envío requiere un MDN síncrono en la configuración del socio");
        }
        boolean signedMdn = requestMdn && (mdn == null ? partner.requestSignedMdn() : "SIGNED".equals(mdn.receiptType()));
        String signing = outgoing == null ? partner.signingAlgorithm()
                : first(outgoing.signingAlgorithm(), partner.signingAlgorithm());
        String encryption = outgoing == null ? partner.encryptionAlgorithm()
                : first(outgoing.encryptionAlgorithm(), partner.encryptionAlgorithm());
        signing = first(signing, "RSA_SHA256");
        encryption = first(encryption, "AES256");
        var instant = Instant.now();
        var recipients = certificates.recipients(partners.certificates(partner.id(), "ENCRYPTION"), instant);
        if (recipients.isEmpty()) throw new IllegalArgumentException("Cargue un certificado de cifrado activo del socio");
        List<X509Certificate> signers = requestMdn
                ? PartnerCertificates.current(partners.certificates(partner.id(), "SIGNATURE"), instant) : List.of();
        if (signedMdn && signers.isEmpty()) throw new IllegalArgumentException("Cargue un certificado de firma activo del socio");

        String to = first(partner.partnerAs2Name(), partner.as2Name());
        var options = new MessageBuilder.Options(signing.replace("RSA_", ""),
                first(partner.micAlgorithm(), MessageBuilder.MIC_ALGORITHM), encryption, recipients, requestMdn, signedMdn);
        var request = new MessageBuilder.Request(identity.as2Id(), to, identity.receiptAddress(),
                content, X12, fileName, identity.get(), recipients.getFirst());
        OutboundMessage message = builder.build(request, options);
        var stored = documents.ingest(partner.as2Name(), "OUTBOUND", content, Map.of(
                "Message-ID", message.messageId(), "AS2-From", identity.as2Id(), "AS2-To", to,
                "X-MIC", message.mic(), "X-MIC-Algorithm", message.micAlgorithm(),
                "X-Signing-Algorithm", signing, "X-Encryption-Algorithm", encryption), user);

        if (stored.duplicate() || !"CREATED".equals(stored.document().status())) {
            return new Outcome(stored.document().id(), message.messageId(), stored.document().status(),
                    message.mic(), false, null, "El documento no superó la validación X12 del repositorio");
        }
        SendResult result;
        try {
            List<X509Certificate> tlsCertificates = "https".equalsIgnoreCase(target.getScheme())
                    ? PartnerCertificates.current(partners.certificates(partner.id(), "SSL"), instant) : List.of();
            try (var http = tls.client(partner.id(), target, timeout(partner.connectionTimeoutSeconds(), 10), tlsCertificates)) {
                result = new As2Client(http, timeout(partner.readTimeoutSeconds(), 60))
                        .send(target, message, signers, requestMdn, signedMdn, partner.validateMic());
            }
        } catch (As2Exception e) {
            return complete(partner.as2Name(), stored.document().id(), message, null, e.getMessage());
        }
        return complete(partner.as2Name(), stored.document().id(), message, result, result.rejection());
    }

    private Outcome complete(String partner, UUID documentId, OutboundMessage message, SendResult result, String error) {
        var mdn = result == null ? null : result.mdn();
        var status = error != null ? FAILED : mdn == null ? "DELIVERED" : "MDN_RECEIVED";
        var receipt = mdn == null || mdn.disposition() == null ? null : new As2Documents.Receipt(mdn.disposition(),
                mdn.receivedContentMic(), result.micMatches(), mdn.signed() ? mdn.signatureValid() : null, result.rawMdn());
        documents.complete(documentId, new As2Documents.Result(status, error, receipt));
        audit.publish(new AuditEvent(error == null ? "DOCUMENT_SENT" : "DOCUMENT_SEND_FAILED",
                error == null ? "SUCCESS" : "FAILURE", partner, message.messageId(),
                error == null ? "Envío AS2 confirmado" : error));
        return new Outcome(documentId, message.messageId(), status, message.mic(), mdn != null,
                mdn == null ? null : mdn.disposition(), error);
    }

    public static URI endpoint(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta la URL AS2 del socio");
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Configure una URL AS2 http(s) válida para el socio");
        }
        return uri;
    }

    private static String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static Duration timeout(Integer value, int fallback) {
        return Duration.ofSeconds(value == null ? fallback : value);
    }

    public record Outcome(UUID documentId, String messageId, String status, String mic, boolean mdnReceived,
                          String mdnDisposition, String reason) {}
}
