package edi.gateway.logging;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Traza de acceso: una linea JSON por peticion con {@code method}, {@code path}, {@code status},
 * {@code latencyMs}, {@code clientIp} y {@code traceId}.
 *
 * <p>Es un {@link WebFilter} (no un filtro de ruteo) y va primero, antes de Spring Security: asi
 * tambien quedan registradas las peticiones que el gateway rechaza (401, 403, 400). Propaga el
 * {@code X-Trace-Id} a los servicios y lo devuelve en la respuesta para seguir una peticion de
 * punta a punta.</p>
 *
 * <p>RN-09: nunca registra tokens, cookies, la consulta (query string) ni el cuerpo.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonAccessLogFilter implements WebFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger("access");

    /** Solo ids razonables: el valor acaba en el log y no debe permitir inyectar contenido. */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String service;

    public JsonAccessLogFilter(@Value("${spring.application.name:gateway}") String service) {
        this.service = service;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        String traceId = traceId(exchange.getRequest().getHeaders().getFirst(TRACE_HEADER));
        // Se toma al entrar: al terminar la conexion puede estar cerrada.
        String clientIp = clientIp(exchange.getRequest());

        ServerWebExchange traced = exchange.mutate()
                .request(r -> r.headers(h -> h.set(TRACE_HEADER, traceId)))
                .build();
        traced.getResponse().getHeaders().set(TRACE_HEADER, traceId);

        return chain.filter(traced).doFinally(signal -> {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            Map<String, Object> entry = entry(traced, traceId, clientIp, latencyMs);
            LoggingEventBuilder event = log.atInfo();
            entry.forEach(event::addKeyValue);
            event.log("access");
        });
    }

    /** Campos de la linea de acceso, en orden estable. Nunca contiene valores nulos. */
    Map<String, Object> entry(ServerWebExchange exchange, String traceId, String clientIp, long latencyMs) {
        ServerHttpRequest req = exchange.getRequest();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("service", service);
        entry.put("traceId", traceId);
        entry.put("method", req.getMethod().name());
        entry.put("path", req.getPath().pathWithinApplication().value());
        entry.put("status", status != null ? status.value() : 0);
        entry.put("latencyMs", latencyMs);
        entry.put("clientIp", clientIp);
        return entry;
    }

    public static String traceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Primera IP de {@code X-Forwarded-For} si llega; si no, la direccion remota; y si tampoco hay,
     * {@code "-"}. Con {@code forward-headers-strategy: framework}, Spring consume la cabecera del
     * proxy y deja la IP del cliente como direccion remota sin resolver: por eso
     * {@code getHostString()}.
     */
    public static String clientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getHostString() != null) {
            return remote.getHostString();
        }
        return "-";
    }
}
