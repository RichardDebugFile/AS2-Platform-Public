package edi.as2adapter.as2;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.security.cert.X509Certificate;

/**
 * Arma un mensaje AS2 1.2 (RFC 4130): el documento como parte MIME, firmado (multipart/signed con
 * firma separada SHA-256) y cifrado para el socio (application/pkcs7-mime, AES-256), con la solicitud
 * de un MDN firmado y sincrono.
 */
public final class MessageBuilder {

    public static final String MIC_ALGORITHM = "sha-256";
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Clock clock;

    public MessageBuilder(Clock clock) {
        this.clock = clock;
    }

    /**
     * Datos de un envio.
     *
     * @param from        AS2-From: identificador AS2 propio
     * @param to          AS2-To: identificador AS2 del socio
     * @param mdnTo       a quien pedir el MDN (Disposition-Notification-To)
     * @param payload     documento tal cual (por ejemplo, un X12)
     * @param contentType tipo MIME del documento ({@code application/edi-x12})
     * @param fileName    nombre con el que el socio lo guarda
     * @param signer      clave y certificado propios
     * @param recipient   certificado de cifrado del socio
     */
    public record Request(String from, String to, String mdnTo, byte[] payload, String contentType, String fileName,
                          As2Identity signer, X509Certificate recipient) {
    }

    public OutboundMessage build(Request r) {
        Map<String, String> contentHeaders = new LinkedHashMap<>();
        contentHeaders.put("Content-Type", r.contentType());
        contentHeaders.put("Content-Transfer-Encoding", "binary");
        contentHeaders.put("Content-Disposition", "attachment; filename=\"" + r.fileName() + "\"");
        byte[] content = Mime.part(contentHeaders, r.payload());

        String mic = Cms.mic(content, MIC_ALGORITHM);
        byte[] signed = signedEntity(content, Cms.sign(content, r.signer()));
        byte[] body = Cms.encrypt(signed, r.recipient());

        String messageId = "<" + ID_TIME.format(clock.instant().atZone(clock.getZone())) + "-" + UUID.randomUUID()
                + "@" + domainOf(r.from()) + ">";
        Map<String, String> http = new LinkedHashMap<>();
        http.put("AS2-Version", "1.2");
        http.put("AS2-From", quote(r.from()));
        http.put("AS2-To", quote(r.to()));
        http.put("Message-ID", messageId);
        http.put("Subject", "Documento " + r.fileName());
        http.put("Mime-Version", "1.0");
        http.put("Content-Type", "application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"");
        http.put("Content-Disposition", "attachment; filename=\"smime.p7m\"");
        http.put("Content-Transfer-Encoding", "binary");
        http.put("Disposition-Notification-To", r.mdnTo());
        http.put("Disposition-Notification-Options",
                "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, " + MIC_ALGORITHM);
        return new OutboundMessage(messageId, http, body, mic, MIC_ALGORITHM);
    }

    /** multipart/signed (RFC 1847): la parte firmada tal cual y la firma en base64. */
    private static byte[] signedEntity(byte[] content, byte[] signature) {
        String b = Mime.boundary();
        Map<String, String> sigHeaders = new LinkedHashMap<>();
        sigHeaders.put("Content-Type", "application/pkcs7-signature; name=\"smime.p7s\"");
        sigHeaders.put("Content-Transfer-Encoding", "base64");
        sigHeaders.put("Content-Disposition", "attachment; filename=\"smime.p7s\"");

        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 4096);
        body.writeBytes(Mime.ascii("--" + b + "\r\n"));
        body.writeBytes(content);
        body.writeBytes(Mime.ascii("\r\n--" + b + "\r\n"));
        body.writeBytes(Mime.part(sigHeaders, Mime.base64Lines(signature)));
        body.writeBytes(Mime.ascii("\r\n--" + b + "--\r\n"));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg="
                + MIC_ALGORITHM + "; boundary=\"" + b + "\"");
        return Mime.part(headers, body.toByteArray());
    }

    /** Los identificadores AS2 con espacios u otros simbolos van entre comillas (RFC 4130 6.2). */
    static String quote(String as2Id) {
        return as2Id.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+") ? as2Id : "\"" + as2Id.replace("\"", "\\\"") + "\"";
    }

    private static String domainOf(String as2Id) {
        String d = as2Id.replaceAll("[^A-Za-z0-9.-]", "").toLowerCase(java.util.Locale.ROOT);
        return d.isEmpty() ? "as2.local" : d;
    }
}
