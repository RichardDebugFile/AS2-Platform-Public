package edi.as2adapter.as2;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lo minimo de MIME (RFC 2045/2046) que necesita AS2: armar una parte con sus cabeceras, partir un
 * multipart en sus partes tal cual llegaron (los bytes exactos importan para verificar firmas y el MIC)
 * y leer cabeceras y parametros. Sin JavaMail: el mensaje se arma y se lee byte a byte.
 */
public final class Mime {

    static final byte[] CRLF = {'\r', '\n'};
    private static final SecureRandom RANDOM = new SecureRandom();

    private Mime() {
    }

    /** Una parte MIME: cabeceras en el orden dado, linea en blanco y contenido. */
    public static byte[] part(Map<String, String> headers, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 256);
        headers.forEach((name, value) -> out.writeBytes(ascii(name + ": " + value + "\r\n")));
        out.writeBytes(CRLF);
        out.writeBytes(body);
        return out.toByteArray();
    }

    /** Delimitador unico para un multipart (RFC 2046 5.1.1). */
    public static String boundary() {
        byte[] r = new byte[12];
        RANDOM.nextBytes(r);
        return "----=_AS2_" + HexFormat.of().formatHex(r);
    }

    /** Contenido en base64 con lineas de 76 caracteres, como exige MIME. */
    public static byte[] base64Lines(byte[] data) {
        return Base64.getMimeEncoder(76, CRLF).encode(data);
    }

    /**
     * Partes de un multipart, cada una con sus cabeceras y sin el salto de linea que precede al
     * siguiente delimitador (ese salto pertenece al delimitador, RFC 2046 5.1.1).
     */
    public static List<byte[]> split(byte[] body, String boundary) {
        byte[] delimiter = ascii("--" + boundary);
        List<byte[]> parts = new ArrayList<>();
        int at = indexOf(body, delimiter, 0);
        if (at < 0) throw new As2Exception("El multipart no contiene el delimitador " + boundary);
        while (true) {
            int lineEnd = indexOf(body, CRLF, at);
            if (lineEnd < 0 || isClosing(body, at + delimiter.length)) break;
            int start = lineEnd + 2;
            int next = indexOf(body, concat(CRLF, delimiter), start);
            if (next < 0) throw new As2Exception("El multipart no cierra su delimitador " + boundary);
            byte[] p = new byte[next - start];
            System.arraycopy(body, start, p, 0, p.length);
            parts.add(p);
            at = next + 2;
        }
        return parts;
    }

    /** Cabeceras (nombres en minusculas, lineas plegadas unidas) y donde empieza el contenido. */
    public static Headers headers(byte[] entity) {
        int end = indexOf(entity, ascii("\r\n\r\n"), 0);
        int bodyStart;
        if (entity.length >= 2 && entity[0] == '\r' && entity[1] == '\n') {
            end = 0;
            bodyStart = 2;
        } else if (end < 0) {
            throw new As2Exception("La parte MIME no separa cabeceras y contenido");
        } else {
            bodyStart = end + 4;
        }
        Map<String, String> map = new LinkedHashMap<>();
        String last = null;
        for (String line : new String(entity, 0, end, StandardCharsets.ISO_8859_1).split("\r\n")) {
            last = addHeaderLine(map, line, last);
        }
        return new Headers(map, bodyStart);
    }

    /** Agrega una linea de cabecera, o la continuacion plegada de la anterior; devuelve la cabecera en curso. */
    private static String addHeaderLine(Map<String, String> map, String line, String last) {
        if (line.isEmpty()) return last;
        if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
            if (last != null) map.put(last, map.get(last) + " " + line.strip());
            return last;
        }
        int colon = line.indexOf(':');
        if (colon <= 0) return last;
        String name = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
        map.put(name, line.substring(colon + 1).strip());
        return name;
    }

    /** Valor de un parametro de cabecera ({@code boundary}, {@code micalg}...), sin comillas. */
    public static String param(String headerValue, String name) {
        if (headerValue == null) return null;
        for (String p : parameters(headerValue)) {
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).strip().equalsIgnoreCase(name)) return unquote(p.substring(eq + 1).strip());
        }
        return null;
    }

    /**
     * Parametros de una cabecera: lo separado por ';' fuera de comillas, sin el primer trozo (el tipo
     * de medio). Se recorre caracter a caracter, sin expresiones regulares.
     */
    private static List<String> parameters(String value) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (char c : value.toCharArray()) {
            if (escaped) {
                escaped = false;
                cur.append(c);
            } else if (c == ';' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                escaped = quoted && c == '\\';
                if (c == '"') quoted = !quoted;
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.subList(1, out.size());
    }

    private static String unquote(String v) {
        return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")
                ? v.substring(1, v.length() - 1).replace("\\\"", "\"")
                : v;
    }

    /** Tipo de medio sin parametros y en minusculas: {@code multipart/signed}. */
    public static String mediaType(String contentType) {
        if (contentType == null) return "";
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).strip().toLowerCase(Locale.ROOT);
    }

    /** Cabeceras de una parte y posicion de su contenido. */
    public record Headers(Map<String, String> values, int bodyStart) {

        public String get(String name) {
            return values.get(name.toLowerCase(Locale.ROOT));
        }

        public byte[] body(byte[] entity) {
            byte[] b = new byte[entity.length - bodyStart];
            System.arraycopy(entity, bodyStart, b, 0, b.length);
            return b;
        }

        /** Contenido ya decodificado segun su Content-Transfer-Encoding (base64 o tal cual). */
        public byte[] decodedBody(byte[] entity) {
            byte[] raw = body(entity);
            String cte = get("content-transfer-encoding");
            return cte != null && cte.equalsIgnoreCase("base64") ? Base64.getMimeDecoder().decode(raw) : raw;
        }
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) out.writeBytes(a);
        return out.toByteArray();
    }

    private static boolean isClosing(byte[] body, int i) {
        return i + 1 < body.length && body[i] == '-' && body[i + 1] == '-';
    }

    static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            if (Arrays.equals(data, i, i + pattern.length, pattern, 0, pattern.length)) return i;
        }
        return -1;
    }
}
