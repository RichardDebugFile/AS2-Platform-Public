package edi.as2adapter.inbound;

import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.Cms;
import edi.as2adapter.as2.InboundMessage;
import edi.as2adapter.as2.LocalIdentity;
import edi.as2adapter.as2.MdnWriter;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.as2.Mime;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.audit.AuditEvent;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.outbound.OutboundService;
import edi.as2adapter.partner.PartnerCertificates;
import edi.as2adapter.partner.PartnerDirectory;
import edi.as2adapter.partner.PartnerDirectory.Agreement;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InboundService {

    private static final String PROCESSED = "processed";
    private static final String ERROR = "processed/Error: ";
    private final PartnerDirectory partners;
    private final LocalIdentity identity;
    private final As2Documents documents;
    private final AuditClient audit;
    private final InboundFollowUp followUp;

    public InboundService(PartnerDirectory partners, LocalIdentity identity, As2Documents documents,
                          AuditClient audit, InboundFollowUp followUp) {
        this.partners = partners;
        this.identity = identity;
        this.documents = documents;
        this.audit = audit;
        this.followUp = followUp;
    }

    public Result receive(Request request, byte[] raw, boolean testMode) {
        String from = Mime.unquote(request.from().strip());
        Agreement agreement = partners.find(from);
        if (agreement == null) throw new IllegalArgumentException("Unknown AS2-From");
        if (!identity.as2Id().equals(Mime.unquote(request.to().strip()))) {
            throw new IllegalArgumentException("Unknown AS2-To");
        }
        if (request.messageId().isBlank() || request.messageId().length() > 255) {
            throw new IllegalArgumentException("Message-ID inválido");
        }
        ReceiptPlan plan = receiptPlan(request, agreement);
        return process(request, raw, testMode, agreement, plan);
    }

    private Result process(Request request, byte[] raw, boolean testMode, Agreement agreement, ReceiptPlan plan) {
        InboundMessage message;
        try {
            message = testMode ? new InboundMessage(raw, raw, null)
                    : InboundMessage.decode(request.contentType(), request.encoding(), raw, identity.get());
        } catch (InboundMessage.DecryptionFailure _) {
            return failure(agreement, request, plan, "decryption-failed", "DECRYPTION_FAILED");
        } catch (As2Exception | IllegalArgumentException _) {
            return failure(agreement, request, plan, "unexpected-processing-error", "DOCUMENT_RECEIVE_FAILED");
        }

        try {
            if (!testMode && !authenticate(agreement, message, request.messageId())) {
                return failure(agreement, request, plan, "authentication-failed", "SIGNATURE_VERIFICATION_FAILED");
            }
            return ingest(request, testMode, agreement, plan, message);
        } catch (As2Exception | IllegalArgumentException _) {
            return failure(agreement, request, plan, "unexpected-processing-error", "DOCUMENT_RECEIVE_FAILED");
        }
    }

    private Result ingest(Request request, boolean testMode, Agreement agreement, ReceiptPlan plan, InboundMessage message) {
        String mic = Cms.mic(message.micSource(), plan.algorithm());
        String partner = agreement.partner().as2Name();
        var stored = documents.ingest(partner, "INBOUND", message.payload(), Map.of(
                "Message-ID", request.messageId(), "AS2-From", Mime.unquote(request.from().strip()),
                "AS2-To", identity.as2Id(), "X-MIC", mic, "X-MIC-Algorithm", plan.algorithm(),
                "X-Document-Attributes", testMode ? "{\"testMode\":true}" : "{}"), null);
        var record = stored.document();
        if (!partner.equals(record.partnerAs2Name()) || !"INBOUND".equals(record.direction())) {
            return failure(agreement, request, plan, "unexpected-processing-error", "DOCUMENT_RECEIVE_FAILED");
        }
        if (stored.duplicate()) {
            audit.publish(new AuditEvent("DUPLICATE_DETECTED", "SUCCESS", partner, request.messageId(),
                    "El documento ya estaba registrado"));
        }
        if ("VALIDATION_FAILED".equals(record.status())) {
            return failure(agreement, request, plan, "unexpected-processing-error", "DOCUMENT_RECEIVE_FAILED");
        }
        Runnable ack = stored.duplicate() || testMode ? null
                : followUp.acknowledgment(agreement, request.messageId(), record.typeCode(), message.payload());
        return reply(agreement, request, plan, PROCESSED, mic, ack);
    }

    private boolean authenticate(Agreement agreement, InboundMessage message, String messageId) {
        var partner = agreement.partner();
        boolean verified = message.signature() != null && PartnerCertificates
                .current(partners.certificates(partner.id(), "SIGNATURE"), Instant.now()).stream()
                .anyMatch(c -> Cms.verify(message.micSource(), message.signature(), c));
        if (verified) return true;
        if (partner.validateSignature()) return false;
        audit.publish(new AuditEvent("SIGNATURE_NOT_REQUIRED", "SUCCESS", partner.as2Name(), messageId,
                "El acuerdo permite recibir este documento sin una firma verificada"));
        return true;
    }

    private Result failure(Agreement agreement, Request request, ReceiptPlan plan, String reason, String event) {
        audit.publish(new AuditEvent(event, "FAILURE", agreement.partner().as2Name(), request.messageId(), reason));
        return reply(agreement, request, plan, ERROR + reason, null, null);
    }

    private Result reply(Agreement agreement, Request request, ReceiptPlan plan, String disposition, String mic, Runnable ack) {
        if (!plan.requested()) return new Result(null, null, ack);
        var body = MdnWriter.write(new MdnWriter.Report(identity.as2Id(), request.messageId(), disposition, mic,
                plan.algorithm()), plan.signed() ? identity.get() : null);
        if (plan.asyncUrl() == null) return new Result(body, null, ack);
        Runnable asyncPost = () -> followUp.postMdn(plan.asyncUrl(), agreement, identity.as2Id(), request.from(),
                request.messageId(), body);
        return new Result(null, asyncPost, ack);
    }

    private static ReceiptPlan receiptPlan(Request request, Agreement agreement) {
        String options = "receipt; " + request.receiptOptions();
        var mdn = agreement.mdn();
        var partner = agreement.partner();
        var protocol = Mime.param(options, "signed-receipt-protocol");
        boolean signed = (mdn == null ? partner.requestSignedMdn() : "SIGNED".equals(mdn.receiptType()))
                || (protocol != null && protocol.toLowerCase(java.util.Locale.ROOT).contains("pkcs7-signature"));
        String algorithm = algorithm(Mime.param(options, "signed-receipt-micalg"), partner.micAlgorithm());
        if (request.notifyTo() == null || request.notifyTo().isBlank()) return new ReceiptPlan(false, signed, algorithm, null);
        return new ReceiptPlan(true, signed, algorithm, asyncTarget(request, agreement));
    }

    private static URI asyncTarget(Request request, Agreement agreement) {
        var mdn = agreement.mdn();
        var partner = agreement.partner();
        if (!"ASYNC".equals(mdn == null ? partner.mdnMode() : mdn.deliveryMode())) return null;
        URI configured = OutboundService.endpoint(mdn == null ? partner.mdnReceiptUrl() : mdn.asyncMdnUrl());
        if (request.deliveryOption() != null && !configured.equals(OutboundService.endpoint(request.deliveryOption()))) {
            throw new IllegalArgumentException("La URL de acuse no coincide con el acuerdo del socio");
        }
        return configured;
    }

    private static String algorithm(String requested, String fallback) {
        if (requested != null) {
            for (String candidate : List.of(requested.split(","))) {
                try {
                    return Cms.mimeDigest(candidate);
                } catch (As2Exception _) {
                    // Los modificadores optional/required y algoritmos desconocidos no son un MIC.
                }
            }
        }
        return Cms.mimeDigest(fallback == null ? MessageBuilder.MIC_ALGORITHM : fallback);
    }

    public record Request(String from, String to, String messageId, String contentType, String encoding,
                          String notifyTo, String receiptOptions, String deliveryOption) {}

    private record ReceiptPlan(boolean requested, boolean signed, String algorithm, URI asyncUrl) {}

    public record Result(MdnWriter.Body body, Runnable asyncMdn, Runnable acknowledgment) {}
}
