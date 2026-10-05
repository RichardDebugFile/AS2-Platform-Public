package edi.auth.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Lee el access token del header {@code Authorization: Bearer} (prioridad) o de la cookie HttpOnly
 * {@code access_token}, que es como lo envia la consola web.
 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    static final String COOKIE_NAME = "access_token";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Rutas donde NO se lee el token: sirven para obtener o cerrar la sesion y el navegador les manda
     * tambien la cookie caducada. Si se leyera, Spring Security la validaria antes del permitAll() y
     * responderia 401 a quien viene precisamente a renovar.
     *
     * <p>Lista explicita y no el prefijo {@code /auth/}: {@code /auth/register} exige ADMIN y ahi el
     * token si hay que leerlo.</p>
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
    public String resolve(HttpServletRequest request) {
        if (rutaSinToken(request.getRequestURI())) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
