package edi.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Escenario de HU-05 "Traza de acceso" y regla RN-09 (sin tokens en el log). */
class JsonAccessLogFilterTest {

    private final JsonAccessLogFilter filter = new JsonAccessLogFilter("gateway");
    private final Logger accessLog = (Logger) LoggerFactory.getLogger("access");
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

    @BeforeEach
    void capturar() {
        captured.start();
        accessLog.addAppender(captured);
    }

    @AfterEach
    void soltar() {
        accessLog.detachAppender(captured);
    }

    private Map<String, Object> ultimaLinea() {
        assertThat(captured.list).isNotEmpty();
        ILoggingEvent event = captured.list.getLast();
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }

    @Test
    void escribeMethodPathStatusLatencyYTraceId() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/partners?token=secreto")
                .header("X-Forwarded-For", "203.0.113.7, 198.51.100.20")
                .cookie(new org.springframework.http.HttpCookie("access_token", "jwt-secreto"))
                .build());
        WebFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Map<String, Object> linea = ultimaLinea();
        assertThat(linea).containsKeys("method", "path", "status", "latencyMs", "traceId", "clientIp", "service");
        assertThat(linea.get("method")).isEqualTo("GET");
        assertThat(linea.get("path")).isEqualTo("/api/partners");
        assertThat(linea.get("status")).isEqualTo(401);
        assertThat(linea.get("clientIp")).isEqualTo("203.0.113.7");
        assertThat((Long) linea.get("latencyMs")).isGreaterThanOrEqualTo(0L);
        // RN-09: ni la consulta ni el token aparecen en la linea.
        assertThat(captured.list.getLast().getKeyValuePairs().stream().map(KeyValuePair::toString))
                .noneMatch(s -> s.contains("secreto"));
    }

    @Test
    void generaTraceIdYLoPropagaALosServiciosYALaRespuesta() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build());
        AtomicReference<String> downstream = new AtomicReference<>();
        filter.filter(exchange, ex -> {
                    downstream.set(ex.getRequest().getHeaders().getFirst("X-Trace-Id"));
                    return Mono.empty();
                })
                .block();

        assertThat(downstream.get()).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(downstream.get());
        assertThat(ultimaLinea().get("traceId")).isEqualTo(downstream.get());
    }

    @Test
    void conservaUnTraceIdValidoYDescartaUnoPeligroso() {
        assertThat(JsonAccessLogFilter.traceId("trace-abc_1.2")).isEqualTo("trace-abc_1.2");
        assertThat(JsonAccessLogFilter.traceId("x\n{\"inyectado\":1}")).isNotEqualTo("x\n{\"inyectado\":1}");
        assertThat(JsonAccessLogFilter.traceId(null)).isNotBlank();
    }

    @Test
    void escribeLaLineaAunqueNoHayaEstadoNiIp() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/as2/receive").build());
        filter.filter(exchange, ex -> Mono.empty()).block();

        Map<String, Object> linea = ultimaLinea();
        assertThat(linea.get("path")).isEqualTo("/as2/receive");
        assertThat(linea.get("clientIp")).isEqualTo("-");
    }

    @Test
    void clientIp_prefiereXForwardedFor_yNuncaEsNull() {
        assertThat(JsonAccessLogFilter.clientIp(MockServerHttpRequest.get("/x")
                        .header("X-Forwarded-For", "203.0.113.7")
                        .build()))
                .isEqualTo("203.0.113.7");
        assertThat(JsonAccessLogFilter.clientIp(MockServerHttpRequest.get("/x")
                        .remoteAddress(new InetSocketAddress("198.51.100.4", 5555))
                        .build()))
                .isEqualTo("198.51.100.4");
        // Lo que deja el ForwardedHeaderTransformer tras consumir el X-Forwarded-For del proxy
        assertThat(JsonAccessLogFilter.clientIp(MockServerHttpRequest.get("/x")
                        .remoteAddress(InetSocketAddress.createUnresolved("203.0.113.9", 0))
                        .build()))
                .isEqualTo("203.0.113.9");
        assertThat(JsonAccessLogFilter.clientIp(MockServerHttpRequest.get("/x").build())).isEqualTo("-");
    }
}
