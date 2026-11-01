package edi.documents.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Usuario de la peticion: el gateway lo envia en {@code X-User-Name}; sin peticion o sin cabecera, "system". */
public final class RequestIdentity {

    public static final String USER_HEADER = "X-User-Name";
    public static final String SYSTEM = "system";
    private static final int MAX_LEN = 64;

    private RequestIdentity() {
    }

    public static String currentUser() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(a -> ((ServletRequestAttributes) a).getRequest())
                .map((HttpServletRequest r) -> r.getHeader(USER_HEADER))
                .map(String::strip)
                .filter(u -> !u.isEmpty())
                .map(u -> u.length() > MAX_LEN ? u.substring(0, MAX_LEN) : u)
                .orElse(SYSTEM);
    }
}
