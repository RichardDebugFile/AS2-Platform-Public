package edi.as2adapter.as2;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.cert.X509Certificate;

/**
 * Arma un mensaje AS2 1.2 (RFC 4130) con los algoritmos y el acuse del acuerdo con el socio.
 */
public final class MessageBuilder {

    public static final String MIC_ALGORITHM = "sha-256";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
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

        // Un record compara los arreglos por referencia: se comparan por contenido.
        @Override
        public boolean equals(Object o) {
            return o instanceof Request(var f, var t, var m, var p, var ct, var fn, var s, var rc)
                    && Objects.equals(from, f) && Objects.equals(to, t) && Objects.equals(mdnTo, m)
                    && Arrays.equals(payload, p) && Objects.equals(contentType, ct) && Objects.equals(fileName, fn)
                    && Objects.equals(signer, s) && Objects.equals(recipient, rc);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(from, to, mdnTo, contentType, fileName, signer, recipient) + Arrays.hashCode(payload);
        }

        /** Sin el contenido del documento ni las claves. */
        @Override
        public String toString() {
            return "Request[from=" + from + ", to=" + to + ", fileName=" + fileName + ", "
                    + (payload == null ? 0 : payload.length) + " bytes]";
        }
    }

    public OutboundMessage build(Request r) {
        return build(r, new Options(MIC_ALGORITHM, MIC_ALGORITHM, "AES256", List.of(r.recipient()), true, true));
    }

    public record Options(String signingDigest, String micDigest, String encryptionAlgorithm,
                          List<X509Certificate> recipients, boolean requestMdn, boolean signedMdn) {
    }

    public OutboundMessage build(Request r, Options options) {
        String name = safeFileName(r.fileName());
        Map<String, String> contentHeaders = new LinkedHashMap<>();
        contentHeaders.put(CONTENT_TYPE, r.contentType());
        contentHeaders.put(CONTENT_TRANSFER_ENCODING, "binary");
        contentHeaders.put(CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"");
        byte[] payload = Mime.canonicalize(r.payload());
        byte[] content = Mime.part(contentHeaders, payload);
        boolean signed = options.signingDigest() != null;
        boolean encrypted = options.encryptionAlgorithm() != null;
        String micAlgorithm = Cms.mimeDigest(options.micDigest());
        String mic = Cms.mic(signed || encrypted ? content : payload, micAlgorithm);
        byte[] entity = signed ? signedEntity(content, r.signer(), options.signingDigest()) : content;

        String messageId = "<" + ID_TIME.format(clock.instant().atZone(clock.getZone())) + "-" + UUID.randomUUID()
                + "@" + domainOf(r.from()) + ">";
        Map<String, String> http = new LinkedHashMap<>();
        http.put("AS2-Version", "1.2");
        http.put("AS2-From", quote(r.from()));
        http.put("AS2-To", quote(r.to()));
        http.put("Message-ID", messageId);
        http.put("Subject", "Documento " + name);
        // RFC 4130 (5.2): fecha del envio; OpenAS2 la cita en el texto del MDN ("received on ...").
        http.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().atZone(ZoneOffset.UTC)));
        http.put("Mime-Version", "1.0");
        byte[] body;
        if (encrypted) {
            http.put(CONTENT_TYPE, "application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"");
            http.put(CONTENT_DISPOSITION, "attachment; filename=\"smime.p7m\"");
            http.put(CONTENT_TRANSFER_ENCODING, "binary");
            body = Cms.encrypt(entity, options.recipients(), options.encryptionAlgorithm());
        } else {
            Mime.Headers entityHeaders = Mime.headers(entity);
            entityHeaders.values().forEach(http::put);
            body = entityHeaders.body(entity);
        }
        if (options.requestMdn()) {
            http.put("Disposition-Notification-To", r.mdnTo());
            if (options.signedMdn()) {
                http.put("Disposition-Notification-Options",
                        "signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, " + micAlgorithm);
            }
        }
        return new OutboundMessage(messageId, http, body, mic, micAlgorithm);
    }

    /** multipart/signed (RFC 1847): la parte firmada tal cual y la firma en base64. */
    static byte[] signedEntity(byte[] content, As2Identity signer, String digest) {
        byte[] signature = Cms.sign(content, signer, digest);
        String b = Mime.boundary();
        Map<String, String> sigHeaders = new LinkedHashMap<>();
        sigHeaders.put(CONTENT_TYPE, "application/pkcs7-signature; name=\"smime.p7s\"");
        sigHeaders.put(CONTENT_TRANSFER_ENCODING, "base64");
        sigHeaders.put(CONTENT_DISPOSITION, "attachment; filename=\"smime.p7s\"");

        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 4096);
        body.writeBytes(Mime.ascii("--" + b + "\r\n"));
        body.writeBytes(content);
        body.writeBytes(Mime.ascii("\r\n--" + b + "\r\n"));
        body.writeBytes(Mime.part(sigHeaders, Mime.base64Lines(signature)));
        body.writeBytes(Mime.ascii("\r\n--" + b + "--\r\n"));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(CONTENT_TYPE, "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg="
                + Cms.mimeDigest(digest) + "; boundary=\"" + b + "\"");
        return Mime.part(headers, body.toByteArray());
    }

    /** Los identificadores AS2 con espacios u otros simbolos van entre comillas (RFC 4130 6.2). */
    static String quote(String as2Id) {
        return as2Id.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+") ? as2Id : "\"" + as2Id.replace("\"", "\\\"") + "\"";
    }

    public static String safeFileName(String fileName) {
        String safe = fileName == null ? "" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return safe.isBlank() || safe.chars().allMatch(c -> c == '.') ? "documento.edi" : safe;
    }

    private static String domainOf(String as2Id) {
        String d = as2Id.replaceAll("[^A-Za-z0-9.-]", "").toLowerCase(java.util.Locale.ROOT);
        return d.isEmpty() ? "as2.local" : d;
    }
}
