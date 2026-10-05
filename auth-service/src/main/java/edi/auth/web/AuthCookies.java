package edi.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookies de sesion: {@code access_token} y {@code refresh_token}, siempre HttpOnly y SameSite=Lax.
 *
 * <p>{@code Secure} solo cuando la peticion llego por HTTPS (directo o via {@code X-Forwarded-Proto}
 * del proxy): el navegador descarta las cookies Secure recibidas por HTTP, y la consola tambien se
 * usa por HTTP dentro de la red local.</p>
 */
@Component
public class AuthCookies {

    static final String ACCESS = "access_token";
    static final String REFRESH = "refresh_token";

    /**
     * La cookie del refresh sobrevive este margen a su token (RN-04). Si muriera en el mismo segundo,
     * el navegador dejaria de enviarla y el servidor solo podria decir "sin cookie" en lugar de
     * SESSION_EXPIRED / IDLE_EXPIRED. Un token caducado se rechaza igual: el margen no regala nada.
     */
    static final long REFRESH_GRACE_SECONDS = 300;

    public void write(HttpServletRequest req, HttpServletResponse res, String access, long accessSeconds,
                      String refresh, Instant refreshExpiresAt) {
        long refreshSeconds = Math.max(0, Duration.between(Instant.now(), refreshExpiresAt).toSeconds())
                + REFRESH_GRACE_SECONDS;
        add(res, cookie(req, ACCESS, access, accessSeconds));
        add(res, cookie(req, REFRESH, refresh, refreshSeconds));
    }

    public void clear(HttpServletRequest req, HttpServletResponse res) {
        add(res, cookie(req, ACCESS, "", 0));
        add(res, cookie(req, REFRESH, "", 0));
    }

    private static ResponseCookie cookie(HttpServletRequest req, String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isSecure(req))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse res, ResponseCookie c) {
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    static boolean isSecure(HttpServletRequest req) {
        return req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
    }
}
