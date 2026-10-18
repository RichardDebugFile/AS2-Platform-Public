package edi.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class CookieOrHeaderBearerTokenConverterTest {

    private final CookieOrHeaderBearerTokenConverter converter = new CookieOrHeaderBearerTokenConverter();

    private String token(MockServerHttpRequest req) {
        Authentication auth = converter.convert(MockServerWebExchange.from(req)).block();
        return auth == null ? null : ((BearerTokenAuthenticationToken) auth).getToken();
    }

    @Test
    void leeLaCookie() {
        assertThat(token(MockServerHttpRequest.get("/api/x")
                        .cookie(new HttpCookie("access_token", "tok-cookie"))
                        .build()))
                .isEqualTo("tok-cookie");
    }

    @Test
    void laCabeceraTienePrioridad() {
        assertThat(token(MockServerHttpRequest.get("/api/x")
                        .header("Authorization", "Bearer tok-header")
                        .cookie(new HttpCookie("access_token", "tok-cookie"))
                        .build()))
                .isEqualTo("tok-header");
    }

    @Test
    void sinCookieNiCabecera_noHayToken() {
        assertThat(token(MockServerHttpRequest.get("/api/x").build())).isNull();
        assertThat(token(MockServerHttpRequest.get("/api/x")
                        .cookie(new HttpCookie("access_token", " "))
                        .build()))
                .isNull();
    }
}
