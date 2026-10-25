package edi.partners.domain;

import java.util.Locale;
import java.util.Map;

/** Lectura tolerante de nombres de algoritmos: los socios los escriben de muchas formas. */
final class AlgorithmAliases {

    private AlgorithmAliases() {
    }

    /** Mayusculas y sin separadores: "sha-256", "SHA_256" y "Sha256" quedan como "SHA256". */
    static String key(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }

    static <E extends Enum<E>> E parse(String value, Map<String, E> aliases, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        E found = aliases.get(key(value));
        if (found == null) {
            throw new IllegalArgumentException("Algoritmo no soportado en " + type.getSimpleName() + ": " + value);
        }
        return found;
    }
}
