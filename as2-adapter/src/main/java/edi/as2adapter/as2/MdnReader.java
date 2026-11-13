package edi.as2adapter.as2;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Lee el MDN sincrono que devuelve el socio: verifica su firma con el certificado del socio
 * (multipart/signed) y extrae del reporte (multipart/report) la disposicion y el MIC.
 */
public final class MdnReader {

    private MdnReader() {
    }

    /**
     * @param contentType cabecera Content-Type de la respuesta HTTP
     * @param body        cuerpo de la respuesta
     * @param partner     certificado de firma del socio
     */
    public static Mdn read(String contentType, byte[] body, X509Certificate partner) {
        String type = Mime.mediaType(contentType);
        if (type.equals("multipart/signed")) {
            List<byte[]> parts = Mime.split(body, boundaryOf(contentType));
            if (parts.size() < 2) throw new As2Exception("El MDN firmado no trae la parte de la firma");
            byte[] report = parts.get(0);
            byte[] sigPart = parts.get(1);
            byte[] signature = Mime.headers(sigPart).decodedBody(sigPart);
            boolean valid = Cms.verify(report, signature, partner);
            Mime.Headers h = Mime.headers(report);
            return parseReport(h.get("content-type"), h.body(report), true, valid);
        }
        if (type.equals("multipart/report")) {
            return parseReport(contentType, body, false, false);
        }
        throw new As2Exception("La respuesta no es un MDN (Content-Type: " + contentType + ")");
    }

    private static Mdn parseReport(String contentType, byte[] body, boolean signed, boolean valid) {
        if (!Mime.mediaType(contentType).equals("multipart/report")) {
            throw new As2Exception("El MDN no contiene un multipart/report (Content-Type: " + contentType + ")");
        }
        String text = null;
        Mime.Headers fields = null;
        byte[] notification = null;
        for (byte[] part : Mime.split(body, boundaryOf(contentType))) {
            Mime.Headers h = Mime.headers(part);
            String partType = Mime.mediaType(h.get("content-type"));
            if (partType.equals("message/disposition-notification")) {
                notification = h.decodedBody(part);
                fields = Mime.headers(withBlankLine(notification));
            } else if (partType.startsWith("text/") && text == null) {
                text = new String(h.decodedBody(part), StandardCharsets.UTF_8).strip();
            }
        }
        if (notification == null) throw new As2Exception("El MDN no trae la parte message/disposition-notification");

        String mic = null;
        String alg = null;
        String rcm = fields.get("received-content-mic");
        if (rcm != null) {
            int comma = rcm.indexOf(',');
            mic = (comma < 0 ? rcm : rcm.substring(0, comma)).strip();
            alg = comma < 0 ? null : rcm.substring(comma + 1).strip();
        }
        return new Mdn(fields.get("original-message-id"), fields.get("disposition"), mic, alg, signed, valid, text);
    }

    /** Los campos del reporte tienen forma de cabeceras; se les agrega el fin de cabeceras si falta. */
    private static byte[] withBlankLine(byte[] fields) {
        String s = new String(fields, StandardCharsets.ISO_8859_1).stripTrailing().replaceAll("\r?\n", "\r\n");
        return (s + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String boundaryOf(String contentType) {
        String b = Mime.param(contentType, "boundary");
        if (b == null) throw new As2Exception("El multipart no indica su boundary: " + contentType);
        return b;
    }
}
