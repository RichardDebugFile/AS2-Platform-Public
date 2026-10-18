package edi.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Pasa el JWT de la cookie HttpOnly {@code access_token} a la cabecera
 * {@code Authorization: Bearer ...} que esperan los servicios de detras.
 *
 * <ul>
 *   <li>No toca {@code /auth/**} ni {@code /.well-known/**}: un token caducado en la cookie no debe
 *       impedir iniciar sesion ni renovarla.</li>
 *   <li>Si la peticion ya trae {@code Authorization}, esa cabecera tiene prioridad.</li>
 * </ul>
 */
@Component
public class CookieToHeaderFilter extends AbstractGatewayFilterFactory<CookieToHeaderFilter.Config> {

    static final String ACCESS_TOKEN_COOKIE = "access_token";

    public CookieToHeaderFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            if (path.startsWith("/auth/") || path.startsWith("/.well-known/")) {
                return chain.filter(exchange);
            }
            if (exchange.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                return chain.filter(exchange);
            }
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE);
            if (cookie == null || cookie.getValue().isEmpty()) {
                return chain.filter(exchange);
            }
            return chain.filter(exchange.mutate()
                    .request(r -> r.header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
                    .build());
        };
    }

    /** Sin propiedades: el filtro se declara en {@code default-filters}. */
    public static class Config {}
}
