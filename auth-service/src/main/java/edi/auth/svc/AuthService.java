package edi.auth.svc;

import edi.auth.domain.RefreshToken;
import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import edi.auth.dto.LoginResponse;
import edi.auth.error.AuthException;
import edi.auth.repo.UserAccountRepository;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la sesion: login, segundo factor y renovacion. No sabe nada de HTTP: el
 * controlador decide como viajan los tokens (cookies).
 */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final SessionPolicy policy;
    private final MfaChallengeStore challenges;
    private final CryptoService crypto;
    private final TotpService totp;

    public AuthService(UserAccountRepository users, PasswordService passwords, JwtService jwt,
                       RefreshTokenService refreshTokens, SessionPolicy policy, MfaChallengeStore challenges,
                       CryptoService crypto, TotpService totp) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.policy = policy;
        this.challenges = challenges;
        this.crypto = crypto;
        this.totp = totp;
    }

    /** Sesion recien emitida: los dos tokens (para las cookies) y la respuesta para la consola. */
    public record IssuedSession(String accessToken, String refreshToken, Instant refreshExpiresAt, LoginResponse body) {}

    /** Resultado del login: sesion abierta, o desafio MFA pendiente (sin sesion). */
    public record LoginOutcome(IssuedSession session, String mfaToken) {
        public boolean mfaRequired() {
            return mfaToken != null;
        }
    }

    /**
     * Valida usuario y contrasena. Si la cuenta tiene MFA, la contrasena correcta NO abre sesion:
     * deja un desafio que se canjea en {@link #verifyMfa}.
     */
    @Transactional
    public LoginOutcome login(String username, String password) {
        UserAccount ua = users.findByUsernameIgnoreCase(username)
                .filter(UserAccount::isActive)
                .orElseThrow(AuthException::badCredentials);
        if (!passwords.matches(password, ua.getPasswordHash())) {
            throw AuthException.badCredentials();
        }
        if (ua.isMfaEnabled()) {
            return new LoginOutcome(null, challenges.create(ua.getUsername()));
        }
        return new LoginOutcome(open(ua, policy.newSession(Instant.now())), null);
    }

    /** Canjea un desafio MFA vigente y un codigo TOTP correcto por una sesion nueva. */
    @Transactional
    public IssuedSession verifyMfa(String mfaToken, String code) {
        String username = challenges.getUsername(mfaToken);
        if (username == null) {
            throw new AuthException(AuthException.MFA_CHALLENGE_EXPIRED, "Desafío MFA inválido o expirado");
        }
        // La cuenta pudo desactivarse mientras el desafio estaba pendiente.
        UserAccount ua = users.findByUsernameIgnoreCase(username)
                .filter(UserAccount::isActive)
                .filter(u -> u.isMfaEnabled() && u.getMfaSecret() != null)
                .orElseThrow(() -> {
                    challenges.consume(mfaToken);
                    return AuthException.badCredentials();
                });
        if (!totp.verifyCode(crypto.decrypt(ua.getMfaSecret()), code)) {
            if (!challenges.registerFailure(mfaToken)) {
                throw new AuthException(AuthException.MFA_ATTEMPTS_EXCEEDED,
                        "Demasiados códigos incorrectos. Vuelve a iniciar sesión.");
            }
            throw new AuthException(AuthException.MFA_INVALID_CODE, "Código MFA inválido");
        }
        challenges.consume(mfaToken);
        return open(ua, policy.newSession(Instant.now()));
    }

    /**
     * Rotacion: el refresh usado se revoca y se emite un par nuevo que hereda el inicio de la sesion
     * (RN-03, RN-05). Cualquier rechazo lanza {@link AuthException} con el motivo.
     *
     * <p>{@code noRollbackFor}: la revocacion de las sesiones de una cuenta desactivada debe quedar
     * guardada aunque la peticion termine en 401.</p>
     */
    @Transactional(noRollbackFor = AuthException.class)
    public IssuedSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(AuthException.UNAUTHORIZED, "No hay refresh token");
        }
        RefreshTokenService.Validation v = refreshTokens.validate(refreshToken);
        if (!v.valid()) {
            throw switch (v.rejection()) {
                case SESSION_EXPIRED -> new AuthException(AuthException.SESSION_EXPIRED,
                        "La sesión alcanzó su duración máxima; vuelva a iniciar sesión");
                case IDLE_EXPIRED -> new AuthException(AuthException.IDLE_EXPIRED,
                        "La sesión se cerró por inactividad; vuelva a iniciar sesión");
                case REVOKED, NOT_FOUND -> new AuthException(AuthException.TOKEN_INVALID, "Refresh token inválido");
            };
        }
        RefreshToken stored = v.token();
        // La cuenta sale del user_id de la FILA, no del subject del JWT: asi no hace falta confiar en
        // un token cuya firma no se ha comprobado.
        UserAccount ua = users.findById(stored.getUserId()).orElseThrow(AuthException::badCredentials);
        if (!ua.isActive()) {
            refreshTokens.revokeAllForUser(ua.getId());
            throw AuthException.badCredentials();
        }
        refreshTokens.revoke(refreshToken);
        return open(ua, policy.rotated(stored.getSessionStartedAt(), Instant.now()));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
        }
    }

    /** Unico punto por el que pasan login, MFA y refresh: firma, persiste y arma la respuesta. */
    private IssuedSession open(UserAccount ua, SessionPolicy.Window window) {
        Set<String> roles = ua.getRoles().stream().map(Role::name).collect(Collectors.toUnmodifiableSet());
        String access = jwt.signAccessToken(ua.getUsername(), roles);
        String refresh = jwt.signRefreshToken(ua.getUsername(), window.expiresAt());
        refreshTokens.issue(ua.getId(), refresh, window);
        LoginResponse body = new LoginResponse(
                ua.getUsername(),
                ua.getEmail(),
                roles,
                policy.accessTtlSeconds(),
                policy.ceiling(window.sessionStartedAt()).toEpochMilli(),
                window.expiresAt().toEpochMilli(),
                policy.idleTimeoutSeconds(),
                policy.sessionMaxSeconds());
        return new IssuedSession(access, refresh, window.expiresAt(), body);
    }
}
