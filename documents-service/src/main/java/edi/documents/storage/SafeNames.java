package edi.documents.storage;

/**
 * Nombres que vienen de fuera (identificador AS2 del socio, tipo, numeros de control) y terminan en una
 * ruta de disco: no pueden escapar de la carpeta del repositorio ni de la del socio.
 */
public final class SafeNames {

    private static final int MAX = 128;

    private SafeNames() {
    }

    /**
     * Identificador AS2 usado como carpeta: el mismo nombre que usa partners-service para la carpeta del
     * socio. Se rechaza si podria salir de la carpeta ({@code ..}, separadores) o lleva caracteres de control.
     */
    public static String folder(String as2Name) {
        String v = as2Name == null ? "" : as2Name.strip();
        if (v.isEmpty() || v.length() > MAX || v.equals(".") || v.equals("..") || v.indexOf('/') >= 0
                || v.indexOf('\\') >= 0 || v.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identificador AS2 de socio no valido: '" + v + "'");
        }
        return v;
    }

    /** Parte de un nombre de archivo: solo letras, digitos, guion y guion bajo; vacio = NA. */
    public static String part(String value) {
        if (value == null || value.isBlank()) {
            return "NA";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.strip().toCharArray()) {
            sb.append((Character.isLetterOrDigit(c) && c < 128) || c == '-' ? c : '_');
            if (sb.length() == 40) {
                break;
            }
        }
        return sb.toString();
    }
}
