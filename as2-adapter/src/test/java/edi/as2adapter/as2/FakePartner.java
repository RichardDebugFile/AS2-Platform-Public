package edi.as2adapter.as2;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Socio AS2 simulado para las pruebas: descifra con su clave, verifica la firma del remitente, calcula
 * el MIC de la parte firmada y arma un MDN firmado. Hace en sentido inverso lo que hace MessageBuilder.
 */
public final class FakePartner {

    /** Lo que el socio recibio. */
    public record Received(byte[] signedPart, byte[] payload, Mime.Headers payloadHeaders, boolean signatureValid,
                           String mic) {
    }

    private final As2Identity identity;

    public FakePartner(As2Identity identity) {
        this.identity = identity;
    }

    public Received receive(byte[] body, X509Certificate sender) {
        byte[] signedEntity = Cms.decrypt(body, identity);
        Mime.Headers outer = Mime.headers(signedEntity);
        List<byte[]> parts = Mime.split(outer.body(signedEntity), Mime.param(outer.get("content-type"), "boundary"));
        byte[] content = parts.get(0);
        byte[] sigPart = parts.get(1);
        byte[] signature = Mime.headers(sigPart).decodedBody(sigPart);
        Mime.Headers ch = Mime.headers(content);
        return new Received(content, ch.body(content), ch, Cms.verify(content, signature, sender),
                Cms.mic(content, Mime.param(outer.get("content-type"), "micalg")));
    }

    /** MDN firmado: devuelve {Content-Type, cuerpo}. */
    public String[] mdn(String originalMessageId, String disposition, String mic) {
        String inner = Mime.boundary();
        String report = "--" + inner + "\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "El mensaje fue recibido.\r\n"
                + "--" + inner + "\r\n"
                + "Content-Type: message/disposition-notification\r\n"
                + "Content-Transfer-Encoding: 7bit\r\n\r\n"
                + "Reporting-UA: socio de prueba\r\n"
                + "Original-Recipient: rfc822; SOCIO\r\n"
                + "Final-Recipient: rfc822; SOCIO\r\n"
                + "Original-Message-ID: " + originalMessageId + "\r\n"
                + "Disposition: " + disposition + "\r\n"
                + (mic == null ? "" : "Received-Content-MIC: " + mic + ", sha-256\r\n")
                + "\r\n--" + inner + "--\r\n";
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "multipart/report; report-type=disposition-notification; boundary=\"" + inner + "\"");
        byte[] reportPart = Mime.part(h, report.getBytes(StandardCharsets.US_ASCII));

        String outer = Mime.boundary();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + outer + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(reportPart);
        out.writeBytes(("\r\n--" + outer + "\r\n"
                + "Content-Type: application/pkcs7-signature; name=smime.p7s\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(Mime.base64Lines(Cms.sign(reportPart, identity)));
        out.writeBytes(("\r\n--" + outer + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return new String[] {
            "multipart/signed; protocol=\"application/pkcs7-signature\"; micalg=sha-256; boundary=\"" + outer + "\"",
            new String(out.toByteArray(), StandardCharsets.ISO_8859_1)
        };
    }
}
