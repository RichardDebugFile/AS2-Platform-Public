package edi.partners.svc;

import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MicAlgorithm;
import edi.partners.domain.SigningAlgorithm;
import edi.partners.dto.AlgorithmCatalog;
import edi.partners.dto.AlgorithmCatalog.Item;
import edi.partners.dto.AlgorithmSuggestion;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Catalogo de algoritmos y recomendacion por defecto: SHA-256 con AES-256 para socios actuales;
 * SHA-1 con 3DES solo cuando el socio exige compatibilidad con implementaciones antiguas.
 */
@Component
public class AlgorithmPolicy {

    private static final String RECOMMENDED = "recommended";
    private static final String OK = "ok";
    private static final String LEGACY = "legacy";

    public AlgorithmCatalog catalog() {
        return new AlgorithmCatalog(
                List.of(item(SigningAlgorithm.RSA_SHA256, "RSA-SHA256", RECOMMENDED),
                        item(SigningAlgorithm.RSA_SHA384, "RSA-SHA384", OK),
                        item(SigningAlgorithm.RSA_SHA512, "RSA-SHA512", OK),
                        item(SigningAlgorithm.RSA_SHA1, "RSA-SHA1", LEGACY)),
                List.of(item(EncryptionAlgorithm.AES256, "AES-256", RECOMMENDED),
                        item(EncryptionAlgorithm.AES192, "AES-192", OK),
                        item(EncryptionAlgorithm.AES128, "AES-128", OK),
                        item(EncryptionAlgorithm.TRIPLE_DES, "3DES", LEGACY)),
                List.of(item(MicAlgorithm.SHA256, "SHA-256", RECOMMENDED),
                        item(MicAlgorithm.SHA384, "SHA-384", OK),
                        item(MicAlgorithm.SHA512, "SHA-512", OK),
                        item(MicAlgorithm.SHA1, "SHA-1", LEGACY)));
    }

    public AlgorithmSuggestion suggest(boolean legacyCompatibility) {
        return legacyCompatibility
                ? new AlgorithmSuggestion(SigningAlgorithm.RSA_SHA1, EncryptionAlgorithm.TRIPLE_DES, MicAlgorithm.SHA1,
                        "Compatibilidad con implementaciones AS2 antiguas: usar solo si el socio lo exige")
                : new AlgorithmSuggestion(SigningAlgorithm.RSA_SHA256, EncryptionAlgorithm.AES256, MicAlgorithm.SHA256,
                        "Recomendado: SHA-256 y AES-256");
    }

    private static Item item(Enum<?> key, String label, String status) {
        return new Item(key.name(), label, status);
    }
}
