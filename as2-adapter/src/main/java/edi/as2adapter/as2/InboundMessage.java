package edi.as2adapter.as2;

import java.util.Base64;
import java.util.List;

/** Extrae la entidad que se firmó y la carga X12 sin alterar los bytes verificados. */
public record InboundMessage(byte[] payload, byte[] micSource, byte[] signature) {

    public static InboundMessage decode(String type, String encoding, byte[] body, As2Identity identity) {
        byte[] decoded = "base64".equalsIgnoreCase(encoding) ? Base64.getMimeDecoder().decode(body) : body;
        String mediaType = Mime.mediaType(type);
        if (mediaType.equals("application/pkcs7-mime") || mediaType.equals("application/x-pkcs7-mime")) {
            byte[] entity;
            try {
                entity = Cms.decrypt(decoded, identity);
            } catch (As2Exception e) {
                throw new DecryptionFailure(e);
            }
            return fromEnvelope(entity);
        }
        return fromParts(type, decoded, decoded);
    }

    private static InboundMessage fromEnvelope(byte[] entity) {
        Mime.Headers headers = Mime.headers(entity);
        return fromParts(headers.get("content-type"), headers.decodedBody(entity), entity);
    }

    private static InboundMessage fromParts(String type, byte[] body, byte[] micSource) {
        if (!Mime.mediaType(type).equals("multipart/signed")) {
            return new InboundMessage(extract(type, body), micSource, null);
        }
        List<byte[]> parts = parts(type, body);
        if (parts.size() != 2) throw new As2Exception("La firma AS2 debe contener dos partes MIME");
        byte[] signed = parts.getFirst();
        byte[] signature = parts.getLast();
        Mime.Headers headers = Mime.headers(signed);
        return new InboundMessage(extract(headers.get("content-type"), headers.decodedBody(signed)),
                signed, Mime.headers(signature).decodedBody(signature));
    }

    private static byte[] extract(String type, byte[] body) {
        return switch (Mime.mediaType(type)) {
            case "application/edi-x12", "application/octet-stream" -> body;
            case "multipart/mixed" -> attachment(type, body);
            default -> throw new As2Exception("Tipo MIME no admitido para un documento X12");
        };
    }

    private static byte[] attachment(String type, byte[] body) {
        for (byte[] part : parts(type, body)) {
            Mime.Headers headers = Mime.headers(part);
            String media = Mime.mediaType(headers.get("content-type"));
            String file = Mime.param(headers.get("content-disposition"), "filename");
            if (media.equals("application/edi-x12") || (file != null && file.toLowerCase(java.util.Locale.ROOT).endsWith(".edi"))) {
                return headers.decodedBody(part);
            }
        }
        throw new As2Exception("El mensaje AS2 no contiene un adjunto X12");
    }

    private static List<byte[]> parts(String type, byte[] body) {
        String boundary = Mime.param(type, "boundary");
        if (boundary == null || boundary.isBlank()) throw new As2Exception("Falta el delimitador MIME");
        return Mime.split(body, boundary);
    }

    public static final class DecryptionFailure extends As2Exception {
        DecryptionFailure(As2Exception cause) {
            super("No se pudo descifrar el mensaje AS2", cause);
        }
    }
}
