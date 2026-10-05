package edi.auth.svc;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reglas de vida de una sesion. Solo aritmetica de instantes, sin BD, para probarla sin levantar nada.
 *
 * <p>Dos limites; manda el que llegue antes:</p>
 * <ul>
 *   <li><b>Techo absoluto</b> ({@code session-max-min}): se cuenta desde el login y renovar no lo
 *       mueve (RN-05).</li>
 *   <li><b>Inactividad</b> ({@code idle-timeout-min}): el servidor solo "ve" actividad cuando el
 *       cliente renueva, y el cliente renueva al caducar el access token. Por eso el refresh vive
 *       {@code idle + access}: quien hizo su ultima peticion justo antes de caducar el access lleva
 *       hasta {@code access} minutos sin que lo sepamos y aun asi conserva sus {@code idle} minutos.</li>
 * </ul>
 */
@Component
public class SessionPolicy {

    private final Duration accessTtl;
    private final Duration idleTimeout;
    private final Duration sessionMax;

    public SessionPolicy(
            @Value("${auth.access-ttl-min}") long accessTtlMin,
            @Value("${auth.idle-timeout-min}") long idleTimeoutMin,
            @Value("${auth.session-max-min}") long sessionMaxMin) {
        if (accessTtlMin <= 0 || idleTimeoutMin <= 0 || sessionMaxMin <= 0) {
            throw new IllegalArgumentException(
                    "auth.access-ttl-min, auth.idle-timeout-min y auth.session-max-min deben ser > 0");
        }
        this.accessTtl = Duration.ofMinutes(accessTtlMin);
        this.idleTimeout = Duration.ofMinutes(idleTimeoutMin);
        this.sessionMax = Duration.ofMinutes(sessionMaxMin);
    }

    /** Ventana de validez de un refresh token concreto. */
    public record Window(Instant sessionStartedAt, Instant expiresAt) {}

    /** Sesion nueva (login o MFA): el techo empieza a contar ahora. */
    public Window newSession(Instant now) {
        return windowFrom(now, now);
    }

    /** Rotacion: hereda el inicio de sesion del token anterior, nunca lo recalcula. */
    public Window rotated(Instant sessionStartedAt, Instant now) {
        return windowFrom(sessionStartedAt, now);
    }

    private Window windowFrom(Instant sessionStartedAt, Instant now) {
        Instant byIdle = now.plus(idleTimeout).plus(accessTtl);
        Instant byCeiling = ceiling(sessionStartedAt);
        return new Window(sessionStartedAt, byIdle.isBefore(byCeiling) ? byIdle : byCeiling);
    }

    /** Instante a partir del cual la sesion no se puede renovar bajo ningun concepto. */
    public Instant ceiling(Instant sessionStartedAt) {
        return sessionStartedAt.plus(sessionMax);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public long idleTimeoutSeconds() {
        return idleTimeout.toSeconds();
    }

    public long sessionMaxSeconds() {
        return sessionMax.toSeconds();
    }
}
