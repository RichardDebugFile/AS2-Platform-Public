package edi.as2adapter.documents;

import edi.as2adapter.as2.As2Exception;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class As2Documents {

    private final RestClient http;

    public As2Documents(@Value("${DOCUMENTS_SERVICE_URL:http://localhost:8084}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(20));
        http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Ingested ingest(String partner, String direction, byte[] content, Map<String, String> headers, String user) {
        try {
            return http.post().uri("/api/documents/ingest/as2?partner={partner}&direction={direction}", partner, direction)
                    .headers(h -> {
                        headers.forEach(h::set);
                        if (user != null && !user.isBlank()) h.set("X-User-Name", user);
                    })
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(content).retrieve().body(Ingested.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400) {
                throw new IllegalArgumentException("El repositorio rechazó el documento X12", e);
            }
            throw new As2Exception("No se pudo registrar el documento en el repositorio", e);
        } catch (RestClientException e) {
            throw new As2Exception("No se pudo registrar el documento en el repositorio", e);
        }
    }

    public void complete(UUID id, Result result) {
        try {
            http.put().uri("/internal/as2/documents/{id}/result", id)
                    .contentType(MediaType.APPLICATION_JSON).body(result).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new As2Exception("No se pudo registrar el resultado del envío " + id, e);
        }
    }

    public record Ingested(String outcome, Document document) {
        public boolean duplicate() {
            return "DUPLICATE".equals(outcome);
        }
    }

    public record Document(UUID id, String status, String typeCode) {}

    public record Receipt(String disposition, String receivedMic, Boolean micMatches, Boolean signatureValid,
                          String rawMdn) {}

    public record Result(String status, String detail, Receipt receipt) {}
}
