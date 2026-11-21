package edi.as2adapter.partner;

import edi.as2adapter.as2.As2Exception;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PartnerDirectory {

    private final RestClient http;

    public PartnerDirectory(@Value("${PARTNERS_SERVICE_URL:http://localhost:8081}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Agreement find(String as2Name) {
        try {
            return http.get().uri("/internal/partners/search?as2Name={name}", as2Name)
                    .retrieve().body(Agreement.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw new As2Exception("No se pudo consultar la configuración del socio", e);
        } catch (RestClientException e) {
            throw new As2Exception("No se pudo consultar la configuración del socio", e);
        }
    }

    public List<Certificate> certificates(UUID id, String usage) {
        try {
            var result = http.get().uri("/internal/partners/{id}/certificates?usage={usage}", id, usage)
                    .retrieve().body(new ParameterizedTypeReference<List<Certificate>>() {});
            return result == null ? List.of() : result;
        } catch (RestClientException e) {
            throw new As2Exception("No se pudieron consultar los certificados " + usage + " del socio", e);
        }
    }

    public record Agreement(Partner partner, Outgoing outgoing, Receipt mdn) {}

    public record Partner(UUID id, String as2Name, String partnerAs2Name, String endpointUrl, String signingAlgorithm,
                          String encryptionAlgorithm, String micAlgorithm, Integer connectionTimeoutSeconds,
                          Integer readTimeoutSeconds, boolean requestMdn, boolean requestSignedMdn, String mdnMode,
                          String mdnReceiptUrl, boolean validateSignature, boolean validateMic) {}

    public record Outgoing(String signingAlgorithm, String encryptionAlgorithm, String mimeType, boolean compression) {}

    public record Receipt(String receiptType, String deliveryMode, String asyncMdnUrl) {}

    public record Certificate(boolean preferred, Instant validFrom, Instant validTo, String pem) {}
}
