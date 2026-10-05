package edi.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    private static MockHttpServletRequest req(String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setRequestURI(path);
        return r;
    }

    @Test
    void headerBearer_tienePrioridadSobreLaCookie() {
        MockHttpServletRequest r = req("/users/me");
        r.addHeader("Authorization", "Bearer HEADER");
        r.setCookies(new Cookie("access_token", "COOKIE"));

        assertThat(resolver.resolve(r)).isEqualTo("HEADER");
    }

    @Test
    void sinHeader_leeLaCookie() {
        MockHttpServletRequest r = req("/users/me");
        r.setCookies(new Cookie("otra", "x"), new Cookie("access_token", "COOKIE"));

        assertThat(resolver.resolve(r)).isEqualTo("COOKIE");
    }

    @Test
    void sinNada_oCookieVacia_null() {
        assertThat(resolver.resolve(req("/users/me"))).isNull();
        MockHttpServletRequest r = req("/users/me");
        r.setCookies(new Cookie("access_token", ""));
        assertThat(resolver.resolve(r)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/refresh", "/auth/logout", "/auth/mfa/verify",
        "/.well-known/jwks.json", "/actuator/health"})
    void rutasDeSesion_ignoranElToken(String path) {
        MockHttpServletRequest r = req(path);
        r.setCookies(new Cookie("access_token", "CADUCADO"));

        assertThat(resolver.resolve(r)).isNull();
    }

    @Test
    void register_siLeeElToken() {
        MockHttpServletRequest r = req("/auth/register");
        r.setCookies(new Cookie("access_token", "ADMIN"));

        assertThat(resolver.resolve(r)).isEqualTo("ADMIN");
        assertThat(CookieBearerTokenResolver.rutaSinToken("/auth/loginx")).isFalse();
    }

    @Test
    void rolesDelToken_aAuthorities() {
        assertThat(SecurityConfig.toAuthorities(List.of("ADMIN", "ROLE_AUDITOR")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_AUDITOR");
        assertThat(SecurityConfig.toAuthorities(null)).isEmpty();
    }
}
