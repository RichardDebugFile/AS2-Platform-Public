package edi.partners.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;

/** Algoritmo del resumen de integridad (MIC) que el socio devuelve en el MDN. */
public enum MicAlgorithm {
    SHA1,
    SHA256,
    SHA384,
    SHA512;

    private static final Map<String, MicAlgorithm> ALIASES = Map.of(
            "SHA1", SHA1, "SHA256", SHA256, "SHA384", SHA384, "SHA512", SHA512);

    @JsonCreator
    public static MicAlgorithm parse(String value) {
        return AlgorithmAliases.parse(value, ALIASES, MicAlgorithm.class);
    }
}
