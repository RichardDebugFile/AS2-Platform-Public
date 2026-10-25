package edi.partners.svc;

import edi.partners.domain.MdnMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de forma de los datos del socio. El identificador AS2 da nombre a sus carpetas, asi que
 * ademas de valido para AS2 debe serlo como nombre de carpeta en Windows y Linux.
 */
public final class PartnerRules {

    static final int AS2_NAME_MAX = 128;
    private static final Pattern FORBIDDEN_FOLDER_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private static final Pattern HOSTNAME = Pattern.compile(
            "(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
    private static final Pattern IPV6_LITERAL = Pattern.compile("\\[[0-9A-Fa-f:.]{2,45}]");

    private PartnerRules() {
    }

    /** Identificador AS2 recortado y valido; si no, {@link IllegalArgumentException} con la regla. */
    public static String as2Name(String raw) {
        return folderName(raw, "El identificador AS2", AS2_NAME_MAX, " porque da nombre a sus carpetas");
    }

    /**
     * Nombre de la carpeta del socio dentro del recurso compartido (un solo nivel, sin rutas). Vacio
     * = sin carpeta compartida.
     */
    public static String sharedFolderName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return folderName(raw, "La carpeta compartida", 255, ": debe ser un nombre de carpeta, no una ruta");
    }

    /** Nombre valido como carpeta en Windows y Linux: ASCII imprimible, sin separadores ni reservados. */
    private static String folderName(String raw, String label, int max, String why) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " es obligatorio");
        }
        String name = raw.strip();
        if (name.length() > max) {
            throw new IllegalArgumentException(label + " admite como maximo " + max + " caracteres");
        }
        if (!PRINTABLE_ASCII.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    label + " solo admite caracteres ASCII imprimibles (sin tildes ni eñes)");
        }
        if (FORBIDDEN_FOLDER_CHARS.matcher(name).find()) {
            throw new IllegalArgumentException(label + " no puede contener \\ / : * ? \" < > |" + why);
        }
        if (name.endsWith(".")) {
            throw new IllegalArgumentException(label + " no puede terminar en punto");
        }
        String base = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (WINDOWS_RESERVED.contains(base.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "'" + name + "' es un nombre reservado del sistema y no puede usarse como carpeta");
        }
        return name;
    }

    /** Host del servidor AS2 del socio, sin protocolo, puerto ni ruta (van en sus propios campos). */
    public static String serverAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La direccion del servidor es obligatoria");
        }
        String host = raw.strip();
        if (host.contains("://")) {
            throw new IllegalArgumentException(
                    "Indique solo el host del servidor (p. ej. as2.socio.com): el protocolo va en el campo de transporte");
        }
        if (!HOSTNAME.matcher(host).matches() && !IPV6_LITERAL.matcher(host).matches()) {
            throw new IllegalArgumentException("Direccion de servidor no valida: " + host);
        }
        return host;
    }

    /** Ruta del endpoint AS2: vacia = sin ruta; si no, empieza por "/". */
    public static String path(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String p = raw.strip();
        if (p.chars().anyMatch(Character::isWhitespace) || p.contains("..") || p.contains("?") || p.contains("#")) {
            throw new IllegalArgumentException("Ruta del endpoint no valida: " + p);
        }
        return p.startsWith("/") ? p : "/" + p;
    }

    /** Con MDN asincrono hace falta la URL HTTPS donde se recibira; con sincrono no se guarda. */
    public static String mdnUrl(MdnMode mode, String raw, String field) {
        if (mode != MdnMode.ASYNC) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatoria cuando el MDN es asincrono");
        }
        String url = raw.strip();
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException(field + " debe ser una URL https://");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(field + " no es una URL valida", e);
        }
        return url;
    }

    /** Texto opcional recortado; vacio = nulo. */
    public static String trim(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
