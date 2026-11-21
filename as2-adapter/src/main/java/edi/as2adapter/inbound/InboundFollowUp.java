package edi.as2adapter.inbound;

import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.MdnWriter;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.audit.AuditEvent;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.outbound.OutboundService;
import edi.as2adapter.partner.PartnerCertificates;
import edi.as2adapter.partner.PartnerDirectory;
import edi.as2adapter.partner.PartnerDirectory.Agreement;
import edi.as2adapter.partner.PartnerTls;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InboundFollowUp {

    private static final Set<String> ACK_TYPES = Set.of("850", "856", "810");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final OutboundService outbound;
    private final As2Documents documents;
    private final PartnerDirectory partners;
    private final PartnerTls tls;
    private final AuditClient audit;
    private final Set<String> disabledForPartners;

    public InboundFollowUp(OutboundService outbound, As2Documents documents, PartnerDirectory partners, PartnerTls tls,
                           AuditClient audit, @Value("${as2.fa-997.disabled-for-partners:}") Set<String> disabledForPartners) {
        this.outbound = outbound;
        this.documents = documents;
        this.partners = partners;
        this.tls = tls;
        this.audit = audit;
        this.disabledForPartners = disabledForPartners;
    }

    void queue(InboundService.Result result) {
        if (result.asyncMdn() != null) executor.execute(result.asyncMdn());
        if (result.acknowledgment() != null) executor.execute(result.acknowledgment());
    }

    Runnable acknowledgment(Agreement agreement, String originalId, String type, byte[] payload) {
        String partnerName = agreement.partner().as2Name();
        if (!ACK_TYPES.contains(type) || disabledForPartners.contains(partnerName)) return null;
        return () -> {
            try {
                byte[] ack = documents.acknowledgment(payload);
                var result = outbound.send(agreement, ack, "acuse-997.edi", null);
                if (result.reason() != null) audit.publish(new AuditEvent("ACK_997_SEND_FAILED", "FAILURE",
                        partnerName, originalId, result.reason()));
            } catch (As2Exception | IllegalArgumentException e) {
                audit.publish(new AuditEvent("ACK_997_SEND_FAILED", "FAILURE", partnerName, originalId, e.getMessage()));
            }
        };
    }

    void postMdn(URI url, Agreement agreement, String from, String to, String originalId, MdnWriter.Body body) {
        var partner = agreement.partner();
        try {
            List<X509Certificate> trust = "https".equalsIgnoreCase(url.getScheme())
                    ? PartnerCertificates.current(partners.certificates(partner.id(), "SSL"), Instant.now()) : List.of();
            try (var client = tls.client(partner.id(), url, OutboundService.timeout(partner.connectionTimeoutSeconds(), 10), trust)) {
                var post = HttpRequest.newBuilder(url).timeout(OutboundService.timeout(partner.readTimeoutSeconds(), 60))
                        .header("AS2-From", MessageBuilder.quote(from)).header("AS2-To", to)
                        .header("AS2-Version", "1.2").header("Mime-Version", "1.0")
                        .header("Message-ID", "<" + UUID.randomUUID() + "@as2.local>")
                        .header("Content-Type", body.contentType())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.content())).build();
                int code = client.send(post, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (code / 100 != 2) throw new As2Exception("El socio respondió HTTP " + code);
            }
            audit.publish(new AuditEvent("AS2_MDN_SENT", "SUCCESS", partner.as2Name(), originalId, "MDN asíncrono enviado"));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            mdnFailed(partner.as2Name(), originalId, "Envío interrumpido");
        } catch (IOException e) {
            mdnFailed(partner.as2Name(), originalId, "No se pudo entregar el MDN al socio");
        } catch (As2Exception e) {
            mdnFailed(partner.as2Name(), originalId, e.getMessage());
        }
    }

    private void mdnFailed(String partner, String originalId, String reason) {
        audit.publish(new AuditEvent("AS2_MDN_SEND_FAILED", "FAILURE", partner, originalId, reason));
    }

    @PreDestroy
    void stop() {
        executor.shutdown();
    }
}
