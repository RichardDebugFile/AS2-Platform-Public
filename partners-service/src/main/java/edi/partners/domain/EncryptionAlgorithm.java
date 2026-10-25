package edi.partners.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;

/** Algoritmo de cifrado S/MIME. Acepta "3DES", "_3DES", "aes-256", etc. */
public enum EncryptionAlgorithm {
    TRIPLE_DES,
    AES128,
    AES192,
    AES256;

    private static final Map<String, EncryptionAlgorithm> ALIASES = Map.of(
            "3DES", TRIPLE_DES, "TRIPLEDES", TRIPLE_DES, "DESEDE", TRIPLE_DES,
            "AES128", AES128, "AES192", AES192, "AES256", AES256);

    @JsonCreator
    public static EncryptionAlgorithm parse(String value) {
        return AlgorithmAliases.parse(value, ALIASES, EncryptionAlgorithm.class);
    }
}
