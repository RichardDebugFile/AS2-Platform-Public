package edi.auth.svc;

import edi.auth.domain.RefreshToken;
import edi.auth.repo.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida de los refresh tokens: emitir, validar, revocar y purgar. En BD solo se guarda el
 * SHA-256 del token, asi una copia de la tabla no sirve para abrir sesiones.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** Cuanto se conserva una fila tras caducar: margen para investigar un incidente. */
    static final Duration RETENCION_TRAS_CADUCAR = Duration.ofDays(1);

    private final RefreshTokenRepository repo;
    private final SessionPolicy policy;

    public RefreshTokenService(RefreshTokenRepository repo, SessionPolicy policy) {
        this.repo = repo;
        this.policy = policy;
    }

    /** Por que se rechaza un refresh token. La consola lo usa para explicar el cierre de sesion. */
    public enum Rejection {
        /** No existe en BD (o ya se purgo). */
        NOT_FOUND,
        /** Revocado: logout, rotacion previa o desactivacion de la cuenta. */
        REVOKED,
        /** Se supero la vida maxima de la sesion contada desde el login. */
        SESSION_EXPIRED,
        /** Se supero la ventana de inactividad. */
        IDLE_EXPIRED
    }

    /** O un token valido o el motivo del rechazo; nunca ambos. */
    public record Validation(RefreshToken token, Rejection rejection) {
        public boolean valid() {
            return token != null;
        }

        public static Validation ok(RefreshToken t) {
            return new Validation(t, null);
        }

        public static Validation rejected(Rejection r) {
            return new Validation(null, r);
        }
    }

    @Transactional
    public RefreshToken issue(UUID userId, String token, SessionPolicy.Window window) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setTokenHash(hash(token));
        rt.setSessionStartedAt(window.sessionStartedAt());
        rt.setExpiresAt(window.expiresAt());
        return repo.save(rt);
    }

    @Transactional(readOnly = true)
    public Validation validate(String token) {
        return validate(token, Instant.now());
    }

    Validation validate(String token, Instant now) {
        RefreshToken rt = repo.findByTokenHash(hash(token)).orElse(null);
        if (rt == null) {
            return Validation.rejected(Rejection.NOT_FOUND);
        }
        if (rt.isRevoked()) {
            return Validation.rejected(Rejection.REVOKED);
        }
        // El techo se comprueba aparte de expires_at aunque issue() ya lo acote: asi un cambio de
        // configuracion que acorte la sesion maxima aplica tambien a las sesiones ya abiertas.
        if (!now.isBefore(policy.ceiling(rt.getSessionStartedAt()))) {
            return Validation.rejected(Rejection.SESSION_EXPIRED);
        }
        if (!now.isBefore(rt.getExpiresAt())) {
            return Validation.rejected(Rejection.IDLE_EXPIRED);
        }
        return Validation.ok(rt);
    }

    @Transactional
    public void revoke(String token) {
        repo.findByTokenHash(hash(token)).ifPresent(rt -> rt.setRevoked(true));
    }

    /**
     * Corta todas las sesiones vivas de un usuario. Sin esto, al desactivarlo o cambiarle los roles
     * sus refresh tokens seguirian valiendo y la sesion se renovaria sola.
     *
     * @return numero de sesiones revocadas
     */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return repo.revokeAllByUserId(userId);
    }

    /** Cada login y cada refresh insertan una fila: sin purga la tabla crece sin limite. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeStale() {
        int borrados = purgeStale(Instant.now());
        if (borrados > 0) {
            log.info("refresh_token: purgadas {} filas caducadas", borrados);
        }
    }

    int purgeStale(Instant now) {
        return repo.deleteExpiredBefore(now.minus(RETENCION_TRAS_CADUCAR));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
