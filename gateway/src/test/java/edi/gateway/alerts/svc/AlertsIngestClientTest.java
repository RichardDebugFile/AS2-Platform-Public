package edi.gateway.alerts.svc;

import static org.assertj.core.api.Assertions.assertThat;

import edi.gateway.alerts.config.AlertsClientProperties;
import edi.gateway.alerts.dto.IngestApiCallRequest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AlertsIngestClientTest {

    private static IngestApiCallRequest payload() {
        return new IngestApiCallRequest("trace", "gateway", "GET", "/api/x", 200, 5L, Instant.now(), "203.0.113.7", Map.of());
    }

    private static AlertsClientProperties props(boolean enabled) {
        AlertsClientProperties p = new AlertsClientProperties();
        p.setEnabled(enabled);
        p.setBaseUrl("http://stub");
        p.setToken("tok");
        return p;
    }

    private static WebClient stub(AtomicInteger calls, HttpStatus status) {
        ExchangeFunction ef = req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(status).build());
        };
        return WebClient.builder().exchangeFunction(ef).build();
    }

    @Test
    void sinConfigurar_noEnvia() {
        AtomicInteger calls = new AtomicInteger();
        new AlertsIngestClient(stub(calls, HttpStatus.OK), props(false)).send(payload()).block();
        assertThat(calls).hasValue(0);
    }

    @Test
    void configurado_envia() {
        AtomicInteger calls = new AtomicInteger();
        new AlertsIngestClient(stub(calls, HttpStatus.ACCEPTED), props(true)).send(payload()).block();
        assertThat(calls).hasValue(1);
    }

    /** Un fallo de alerts-service nunca se propaga a la peticion del usuario. */
    @Test
    void unErrorSeIgnora() {
        AtomicInteger calls = new AtomicInteger();
        new AlertsIngestClient(stub(calls, HttpStatus.INTERNAL_SERVER_ERROR), props(true)).send(payload()).block();
        WebClient roto = WebClient.builder()
                .exchangeFunction(req -> Mono.error(new IllegalStateException("caido")))
                .build();
        new AlertsIngestClient(roto, props(true)).send(payload()).block();
        assertThat(calls).hasValue(1);
    }
}
