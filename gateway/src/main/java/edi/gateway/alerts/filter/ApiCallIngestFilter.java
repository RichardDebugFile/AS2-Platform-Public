package edi.gateway.alerts.filter;

import edi.gateway.alerts.config.AlertsClientProperties;
import edi.gateway.alerts.dto.IngestApiCallRequest;
import edi.gateway.alerts.svc.AlertsIngestClient;
import edi.gateway.logging.JsonAccessLogFilter;
import java.time.Instant;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Envia a alerts-service un evento por cada llamada a la API, de forma asincrona (no espera la
 * respuesta). Desactivado por configuracion hasta que existan las reglas de alerta (HU-21).
 */
@Component
public class ApiCallIngestFilter implements GlobalFilter, Ordered {

    private final AlertsIngestClient client;
    private final AlertsClientProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ApiCallIngestFilter(AlertsIngestClient client, AlertsClientProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.isConfigured()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();
        for (String pattern : props.getExcludePaths()) {
            if (matcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            long latencyMs = Math.max(0, (System.nanoTime() - start) / 1_000_000);
            HttpStatusCode sc = exchange.getResponse().getStatusCode();
            String traceId = JsonAccessLogFilter.traceId(req.getHeaders().getFirst(JsonAccessLogFilter.TRACE_HEADER));
            IngestApiCallRequest payload = new IngestApiCallRequest(
                    traceId,
                    props.getServiceName(),
                    req.getMethod().name(),
                    path,
                    sc != null ? sc.value() : 0,
                    latencyMs,
                    Instant.now(),
                    JsonAccessLogFilter.clientIp(req),
                    Map.of());
            client.send(payload).subscribe();
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
