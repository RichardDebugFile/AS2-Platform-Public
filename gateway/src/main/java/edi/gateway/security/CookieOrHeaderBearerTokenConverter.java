package edi.gateway.security;

import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * El navegador manda el JWT en la cookie HttpOnly {@code access_token}, no en {@code Authorization}.
 * {@code CookieToHeaderFilter} la convierte para los servicios de detras, pero es un filtro de ruteo
 * y corre DESPUES de Spring Security, asi que aqui se lee la cookie directamente. La cabecera, si
 * viene (clientes API, scripts), tiene prioridad: el mismo criterio que el filtro.
 */
class CookieOrHeaderBearerTokenConverter implements ServerAuthenticationConverter {

    static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final ServerBearerTokenAuthenticationConverter header = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return header.convert(exchange).switchIfEmpty(Mono.defer(() -> fromCookie(exchange)));
    }

    private Mono<Authentication> fromCookie(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            return Mono.empty();
        }
        return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
    }
}
