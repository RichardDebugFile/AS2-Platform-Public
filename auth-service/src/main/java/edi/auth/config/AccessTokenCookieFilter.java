package edi.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Convierte la cookie HttpOnly {@code access_token} de la consola en {@code Authorization: Bearer}
 * para que el resource server la valide.
 *
 * <p>Va DESPUES del filtro CSRF y no como {@code BearerTokenResolver}: Spring Security exime de CSRF
 * a las peticiones que su resolver reconoce como "con token". Si el resolver leyera la cookie, toda
 * peticion de la consola quedaria exenta, justo las que el navegador adjunta solo. Asi, cuando se
 * evalua CSRF solo cuenta un {@code Authorization} enviado de verdad por el cliente.</p>
 *
 * <p>No es un {@code @Component}: Spring Boot lo registraria ademas como filtro de servlet global.</p>
 */
public class AccessTokenCookieFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "access_token";

    /**
     * Rutas donde la cookie NO se usa: sirven para obtener o cerrar la sesion y el navegador les manda
     * tambien la cookie caducada. Si se validara, responderian 401 a quien viene precisamente a
     * renovar. Lista explicita y no el prefijo {@code /auth/}: {@code /auth/register} exige ADMIN.
     */
    private static final String[] SIN_TOKEN = {
        "/auth/login", "/auth/refresh", "/auth/logout", "/auth/mfa/", "/.well-known/", "/actuator/"
    };

    static boolean rutaSinToken(String path) {
        for (String p : SIN_TOKEN) {
            String prefijo = p.endsWith("/") ? p : p + "/";
            if (path.equals(p) || path.startsWith(prefijo)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = req.getHeader(HttpHeaders.AUTHORIZATION) == null && !rutaSinToken(req.getRequestURI())
                ? cookie(req)
                : null;
        chain.doFilter(token == null ? req : new WithBearer(req, "Bearer " + token), res);
    }

    private static String cookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    /** La peticion original con la cabecera Authorization anadida. */
    private static final class WithBearer extends HttpServletRequestWrapper {
        private final String authorization;

        private WithBearer(HttpServletRequest req, String authorization) {
            super(req);
            this.authorization = authorization;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name) ? authorization : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(authorization))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.add(HttpHeaders.AUTHORIZATION);
            return Collections.enumeration(names);
        }
    }
}
