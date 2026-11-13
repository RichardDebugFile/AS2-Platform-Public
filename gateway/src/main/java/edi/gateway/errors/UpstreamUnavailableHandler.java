package edi.gateway.errors;

import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Servicio de destino caido o detenido (AS2-99): la conexion se rechaza o su nombre ya no resuelve en
 * la red de Docker. Sin este manejador la consola recibia un 500 generico; ahora recibe 503 con el
 * servicio que no responde. Va antes del manejador de errores de Spring Boot (orden -1); cualquier
 * otro error sigue hacia el.
 */
@Component
@Order(-2)
public class UpstreamUnavailableHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!unreachable(ex) || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String service = route == null ? "de destino" : route.getId();
        String message = "El servicio " + service + " no responde";
        String body = "{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"" + message + "\",\"error\":\"" + message + "\"}";
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** Recorre las causas: la de Netty suele venir envuelta. */
    static boolean unreachable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException || t instanceof ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
