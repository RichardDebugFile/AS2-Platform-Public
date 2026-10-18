package edi.gateway.alerts.filter;

import static org.assertj.core.api.Assertions.assertThat;

import edi.gateway.alerts.config.AlertsClientProperties;
import edi.gateway.alerts.dto.IngestApiCallRequest;
import edi.gateway.alerts.svc.AlertsIngestClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ApiCallIngestFilterTest {

    static class Captura extends AlertsIngestClient {
        final AtomicReference<IngestApiCallRequest> last = new AtomicReference<>();

        Captura(AlertsClientProperties props) {
            super(WebClient.create(), props);
        }

        @Override
        public Mono<Void> send(IngestApiCallRequest payload) {
            last.set(payload);
            return Mono.empty();
        }
    }

    private static AlertsClientProperties props() {
        AlertsClientProperties p = new AlertsClientProperties();
        p.setEnabled(true);
        p.setBaseUrl("http://localhost:8082");
        p.setToken("test-token");
        p.setExcludePaths(List.of("/actuator/**", "/internal/**", "/api/alerts/**"));
        return p;
    }

    private static final GatewayFilterChain OK = ex -> {
        ex.getResponse().setStatusCode(HttpStatus.OK);
        return Mono.empty();
    };

    @Test
    void enviaElEventoDeUnaRutaNoExcluida() {
        var p = props();
        var cap = new Captura(p);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/partners/123")
                .header("X-Forwarded-For", "203.0.113.7")
                .build());

        new ApiCallIngestFilter(cap, p).filter(exchange, OK).block();

        IngestApiCallRequest ev = cap.last.get();
        assertThat(ev).isNotNull();
        assertThat(ev.method()).isEqualTo("GET");
        assertThat(ev.path()).isEqualTo("/api/partners/123");
        assertThat(ev.status()).isEqualTo(200);
        assertThat(ev.clientIp()).isEqualTo("203.0.113.7");
        assertThat(ev.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void noEnviaEnRutasExcluidasNiDesactivado() {
        var p = props();
        var cap = new Captura(p);
        new ApiCallIngestFilter(cap, p)
                .filter(MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build()), OK)
                .block();
        assertThat(cap.last.get()).isNull();

        p.setEnabled(false);
        new ApiCallIngestFilter(cap, p)
                .filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/partners").build()), OK)
                .block();
        assertThat(cap.last.get()).isNull();
    }
}
