package edi.auth.svc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Desafios MFA pendientes: un login con contrasena correcta de una cuenta con MFA deja uno, y
 * {@code /auth/mfa/verify} lo canjea por la sesion al recibir el codigo TOTP. Son de un solo uso.
 *
 * <p>Cada desafio admite {@code maxAttempts} codigos fallidos; al agotarlos se descarta y hay que
 * volver a poner la contrasena. Sin limite, los 5 minutos de vigencia darian para probar codigos de
 * 6 digitos a ciegas.</p>
 *
 * <p>En memoria: con una sola instancia de auth-service basta, y un reinicio solo obliga a repetir
 * el login.</p>
 */
@Service
public class MfaChallengeStore {

    private static final class Challenge {
        private final String username;
        private final Instant expiresAt;
        private int failures;

        private Challenge(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    private final long ttlSeconds;
    private final int maxAttempts;
    private final Map<String, Challenge> store = new ConcurrentHashMap<>();

    public MfaChallengeStore(
            @Value("${auth.mfa.challenge-ttl-sec:300}") long ttlSeconds,
            @Value("${auth.mfa.max-attempts:5}") int maxAttempts) {
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
    }

    public String create(String username) {
        purgeExpired();
        String token = UUID.randomUUID().toString();
        store.put(token, new Challenge(username, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    /** Usuario del desafio, o {@code null} si no existe o caduco. */
    public String getUsername(String token) {
        Challenge c = store.get(token);
        if (c == null) {
            return null;
        }
        if (!Instant.now().isBefore(c.expiresAt)) {
            store.remove(token);
            return null;
        }
        return c.username;
    }

    /**
     * Anota un codigo fallido. {@code true} si el desafio sigue vivo; {@code false} si con este fallo
     * se agotaron los intentos (y ya se descarto).
     */
    public boolean registerFailure(String token) {
        Challenge c = store.get(token);
        if (c == null) {
            return false;
        }
        synchronized (c) {
            c.failures++;
            if (c.failures >= maxAttempts) {
                store.remove(token);
                return false;
            }
            return true;
        }
    }

    public void consume(String token) {
        store.remove(token);
    }

    int size() {
        return store.size();
    }

    /** Los desafios abandonados (contrasena sin codigo) se limpian al crear uno nuevo. */
    private void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiresAt));
    }
}
