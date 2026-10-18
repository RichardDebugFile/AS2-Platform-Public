package edi.gateway.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class CookieToHeaderFilterTest {

    private final GatewayFilter filter = new CookieToHeaderFilter().apply(new CookieToHeaderFilter.Config());

    /** Ejecuta el filtro y devuelve la cabecera Authorization que recibe el servicio de destino. */
    private String authorizationDownstream(MockServerHttpRequest req) {
        AtomicReference<String> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            seen.set(ex.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(req), chain).block();
        return seen.get();
    }

    @Test
    void laCookiePasaAAuthorizationBearer() {
        var req = MockServerHttpRequest.get("/api/partners")
                .cookie(new HttpCookie("access_token", "tok123"))
                .build();
        assertThat(authorizationDownstream(req)).isEqualTo("Bearer tok123");
    }

    @Test
    void noTocaLasRutasDeAutenticacion() {
        var login = MockServerHttpRequest.post("/auth/refresh")
                .cookie(new HttpCookie("access_token", "caducado"))
                .build();
        var jwks = MockServerHttpRequest.get("/.well-known/jwks.json")
                .cookie(new HttpCookie("access_token", "tok123"))
                .build();
        assertThat(authorizationDownstream(login)).isNull();
        assertThat(authorizationDownstream(jwks)).isNull();
    }

    @Test
    void laCabeceraExistenteTienePrioridad() {
        var req = MockServerHttpRequest.get("/api/partners")
                .header(HttpHeaders.AUTHORIZATION, "Bearer original")
                .cookie(new HttpCookie("access_token", "tok123"))
                .build();
        assertThat(authorizationDownstream(req)).isEqualTo("Bearer original");
    }

    @Test
    void sinCookieOVacia_noAnadeNada() {
        assertThat(authorizationDownstream(MockServerHttpRequest.get("/api/partners").build())).isNull();
        assertThat(authorizationDownstream(MockServerHttpRequest.get("/api/partners")
                        .cookie(new HttpCookie("access_token", ""))
                        .build()))
                .isNull();
    }
}
