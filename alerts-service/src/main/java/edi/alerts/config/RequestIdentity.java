package edi.alerts.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Quien hace la peticion en curso. alerts-service no valida tokens: el gateway ya lo hizo, exige el
 * rol ADMIN para {@code /api/smtp-config/**} y reenvia el usuario autenticado en {@code X-User-Name}.
 */
public final class RequestIdentity {

    public static final String USER_HEADER = "X-User-Name";
    public static final String SYSTEM = "system";
    private static final int MAX_LEN = 64;

    private RequestIdentity() {
    }

    public static String currentUser() {
        return request()
                .map(r -> r.getHeader(USER_HEADER))
                .filter(s -> !s.isBlank())
                .map(s -> s.length() <= MAX_LEN ? s.trim() : s.substring(0, MAX_LEN).trim())
                .orElse(SYSTEM);
    }

    private static Optional<HttpServletRequest> request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? Optional.of(a.getRequest())
                : Optional.empty();
    }
}
