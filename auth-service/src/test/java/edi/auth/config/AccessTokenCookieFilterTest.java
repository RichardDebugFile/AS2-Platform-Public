package edi.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;

class AccessTokenCookieFilterTest {

    private final AccessTokenCookieFilter filter = new AccessTokenCookieFilter();

    /** Ejecuta el filtro y devuelve la peticion que ve el resto de la cadena. */
    private HttpServletRequest run(MockHttpServletRequest req) throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest r, ServletResponse s) {
                seen.set((HttpServletRequest) r);
            }
        });
        return seen.get();
    }

    private static MockHttpServletRequest req(String path, Cookie... cookies) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setRequestURI(path);
        if (cookies.length > 0) {
            r.setCookies(cookies);
        }
        return r;
    }

    @Test
    void cookie_seConvierteEnBearer() throws Exception {
        HttpServletRequest out = run(req("/users/me", new Cookie("otra", "x"), new Cookie("access_token", "JWT")));

        assertThat(out.getHeader("Authorization")).isEqualTo("Bearer JWT");
        assertThat(Collections.list(out.getHeaders("authorization"))).containsExactly("Bearer JWT");
        assertThat(Collections.list(out.getHeaderNames())).contains("Authorization");
        assertThat(out.getHeader("Accept")).isNull();
    }

    @Test
    void authorizationExplicito_tienePrioridad() throws Exception {
        MockHttpServletRequest r = req("/users/me", new Cookie("access_token", "COOKIE"));
        r.addHeader("Authorization", "Bearer HEADER");

        assertThat(run(r).getHeader("Authorization")).isEqualTo("Bearer HEADER");
    }

    @Test
    void sinCookieOVacia_noAnadeNada() throws Exception {
        assertThat(run(req("/users/me")).getHeader("Authorization")).isNull();
        assertThat(run(req("/users/me", new Cookie("access_token", ""))).getHeader("Authorization")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/refresh", "/auth/logout", "/auth/mfa/verify",
        "/.well-known/jwks.json", "/actuator/health"})
    void rutasDeSesion_ignoranLaCookie(String path) throws Exception {
        assertThat(run(req(path, new Cookie("access_token", "CADUCADO"))).getHeader("Authorization")).isNull();
    }

    @Test
    void register_siUsaLaCookie() throws Exception {
        assertThat(run(req("/auth/register", new Cookie("access_token", "ADMIN"))).getHeader("Authorization"))
                .isEqualTo("Bearer ADMIN");
        assertThat(AccessTokenCookieFilter.rutaSinToken("/auth/loginx")).isFalse();
    }

    @Test
    void rolesDelToken_aAuthorities() {
        assertThat(SecurityConfig.toAuthorities(List.of("ADMIN", "ROLE_AUDITOR")))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_AUDITOR");
        assertThat(SecurityConfig.toAuthorities(null)).isEmpty();
    }
}
