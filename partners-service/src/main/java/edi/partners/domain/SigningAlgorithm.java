package edi.partners.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;

/** Algoritmo de firma S/MIME. Acepta "SHA256", "sha-256", "RSA-SHA256", etc. */
public enum SigningAlgorithm {
    RSA_SHA1,
    RSA_SHA256,
    RSA_SHA384,
    RSA_SHA512;

    private static final Map<String, SigningAlgorithm> ALIASES = Map.of(
            "SHA1", RSA_SHA1, "RSASHA1", RSA_SHA1,
            "SHA256", RSA_SHA256, "RSASHA256", RSA_SHA256,
            "SHA384", RSA_SHA384, "RSASHA384", RSA_SHA384,
            "SHA512", RSA_SHA512, "RSASHA512", RSA_SHA512);

    @JsonCreator
    public static SigningAlgorithm parse(String value) {
        return AlgorithmAliases.parse(value, ALIASES, SigningAlgorithm.class);
    }
}
