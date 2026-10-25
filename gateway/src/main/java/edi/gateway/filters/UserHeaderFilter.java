package edi.gateway.filters;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Informa a los servicios de quien hace la peticion: {@code X-User-Name} con el usuario del JWT ya
 * validado (claim {@code sub}). La cabecera que mande el cliente se descarta siempre, para que nadie
 * pueda hacerse pasar por otro; en las rutas publicas la peticion llega sin ella.
 */
@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_HEADER = "X-User-Name";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sinCabecera = exchange.mutate()
                .request(r -> r.headers(h -> h.remove(USER_HEADER)))
                .build();
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !name.isBlank())
                .map(name -> sinCabecera.mutate().request(r -> r.header(USER_HEADER, name)).build())
                .defaultIfEmpty(sinCabecera)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
