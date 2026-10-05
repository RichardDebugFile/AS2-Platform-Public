package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.auth.domain.RefreshToken;
import edi.auth.domain.Role;
import edi.auth.domain.UserAccount;
import edi.auth.error.AuthException;
import edi.auth.repo.UserAccountRepository;
import edi.auth.svc.AuthService.IssuedSession;
import edi.auth.svc.AuthService.LoginOutcome;
import edi.auth.svc.RefreshTokenService.Rejection;
import edi.auth.svc.RefreshTokenService.Validation;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AuthServiceTest {

    private UserAccountRepository users;
    private PasswordService passwords;
    private JwtService jwt;
    private RefreshTokenService refreshTokens;
    private MfaChallengeStore challenges;
    private CryptoService crypto;
    private TotpService totp;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        passwords = mock(PasswordService.class);
        jwt = mock(JwtService.class);
        refreshTokens = mock(RefreshTokenService.class);
        challenges = mock(MfaChallengeStore.class);
        crypto = mock(CryptoService.class);
        totp = mock(TotpService.class);
        when(jwt.signAccessToken(any(), any())).thenReturn("ACCESS");
        when(jwt.signRefreshToken(any(), any())).thenReturn("REFRESH");
        // SessionPolicy real: es aritmetica pura y mockearla esconderia errores de calculo.
        auth = new AuthService(users, passwords, jwt, refreshTokens, new SessionPolicy(5, 15, 60), challenges,
                crypto, totp);
    }

    private UserAccount user(boolean active, boolean mfa) {
        UserAccount ua = new UserAccount();
        ua.setId(UUID.randomUUID());
        ua.setUsername("alice");
        ua.setEmail("alice@example.com");
        ua.setPasswordHash("HASH");
        ua.setRoles(EnumSet.of(Role.ADMIN));
        ua.setActive(active);
        ua.setMfaEnabled(mfa);
        ua.setMfaSecret(mfa ? "ENC" : null);
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(ua));
        when(users.findById(ua.getId())).thenReturn(Optional.of(ua));
        return ua;
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AuthException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    // ---- login ------------------------------------------------------------------------------

    @Test
    void login_correcto_abreSesionConTiemposDePolitica() {
        UserAccount ua = user(true, false);
        when(passwords.matches("password1", "HASH")).thenReturn(true);

        LoginOutcome out = auth.login("alice", "password1");

        assertThat(out.mfaRequired()).isFalse();
        IssuedSession s = out.session();
        assertThat(s.accessToken()).isEqualTo("ACCESS");
        assertThat(s.refreshToken()).isEqualTo("REFRESH");
        assertThat(s.body().roles()).containsExactly("ADMIN");
        assertThat(s.body().expiresIn()).isEqualTo(300);
        assertThat(s.body().idleTimeoutSeconds()).isEqualTo(900);
        assertThat(s.body().sessionMaxSeconds()).isEqualTo(3600);
        assertThat(s.body().sessionExpiresAt()).isGreaterThan(s.body().refreshExpiresAt());
        verify(refreshTokens).issue(eq(ua.getId()), eq("REFRESH"), any());
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void login_contrasenaMalaOCuentaInactiva_mensajeGenerico(boolean active, boolean passwordOk) {
        user(active, false);
        when(passwords.matches(any(), any())).thenReturn(passwordOk);

        assertThatThrownBy(() -> auth.login("alice", "password1"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Usuario o contraseña inválidos");
        verify(refreshTokens, never()).issue(any(), any(), any());
    }

    @Test
    void login_usuarioInexistente_mismoMensaje() {
        when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login("ghost", "password1")).hasMessage("Usuario o contraseña inválidos");
    }

    @Test
    void login_conMfa_devuelveDesafioSinSesion() {
        user(true, true);
        when(passwords.matches(any(), any())).thenReturn(true);
        when(challenges.create("alice")).thenReturn("CH-1");

        LoginOutcome out = auth.login("alice", "password1");

        assertThat(out.mfaRequired()).isTrue();
        assertThat(out.mfaToken()).isEqualTo("CH-1");
        assertThat(out.session()).isNull();
        verify(jwt, never()).signAccessToken(any(), any());
    }

    // ---- MFA --------------------------------------------------------------------------------

    @Test
    void mfa_codigoCorrecto_abreSesionYConsumeElDesafio() {
        user(true, true);
        when(challenges.getUsername("CH-1")).thenReturn("alice");
        when(crypto.decrypt("ENC")).thenReturn("SECRET");
        when(totp.verifyCode("SECRET", "123456")).thenReturn(true);

        IssuedSession s = auth.verifyMfa("CH-1", "123456");

        assertThat(s.body().username()).isEqualTo("alice");
        verify(challenges).consume("CH-1");
    }

    @Test
    void mfa_desafioCaducado() {
        when(challenges.getUsername("X")).thenReturn(null);

        assertCode(() -> auth.verifyMfa("X", "123456"), AuthException.MFA_CHALLENGE_EXPIRED);
    }

    @Test
    void mfa_cuentaDesactivadaDuranteElDesafio_noAbreSesion() {
        user(false, true);
        when(challenges.getUsername("CH-1")).thenReturn("alice");

        assertCode(() -> auth.verifyMfa("CH-1", "123456"), AuthException.UNAUTHORIZED);
        verify(challenges).consume("CH-1");
    }

    @Test
    void mfa_codigoIncorrecto_cuentaElFallo() {
        user(true, true);
        when(challenges.getUsername("CH-1")).thenReturn("alice");
        when(crypto.decrypt("ENC")).thenReturn("SECRET");
        when(challenges.registerFailure("CH-1")).thenReturn(true);

        assertCode(() -> auth.verifyMfa("CH-1", "000000"), AuthException.MFA_INVALID_CODE);
        verify(challenges, never()).consume(any());
    }

    @Test
    void mfa_ultimoIntento_pideVolverAEntrar() {
        user(true, true);
        when(challenges.getUsername("CH-1")).thenReturn("alice");
        when(crypto.decrypt("ENC")).thenReturn("SECRET");
        when(challenges.registerFailure("CH-1")).thenReturn(false);

        assertCode(() -> auth.verifyMfa("CH-1", "000000"), AuthException.MFA_ATTEMPTS_EXCEEDED);
    }

    // ---- refresh ----------------------------------------------------------------------------

    private RefreshToken row(UserAccount ua, Instant startedAt) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(ua.getId());
        rt.setSessionStartedAt(startedAt);
        rt.setExpiresAt(Instant.now().plusSeconds(600));
        return rt;
    }

    @Test
    void refresh_rotaYHeredaElInicioDeSesion() {
        UserAccount ua = user(true, false);
        Instant inicio = Instant.now().minusSeconds(1200);
        when(refreshTokens.validate("OLD")).thenReturn(Validation.ok(row(ua, inicio)));

        IssuedSession s = auth.refresh("OLD");

        verify(refreshTokens).revoke("OLD");
        assertThat(s.body().sessionExpiresAt()).isEqualTo(inicio.plusSeconds(3600).toEpochMilli());
    }

    @ParameterizedTest
    @CsvSource({
        "NOT_FOUND,TOKEN_INVALID",
        "REVOKED,TOKEN_INVALID",
        "IDLE_EXPIRED,IDLE_EXPIRED",
        "SESSION_EXPIRED,SESSION_EXPIRED"
    })
    void refresh_rechazado_traduceElMotivo(Rejection rejection, String code) {
        when(refreshTokens.validate("T")).thenReturn(Validation.rejected(rejection));

        assertCode(() -> auth.refresh("T"), code);
    }

    @Test
    void refresh_sinToken() {
        assertCode(() -> auth.refresh(null), AuthException.UNAUTHORIZED);
        assertCode(() -> auth.refresh(" "), AuthException.UNAUTHORIZED);
    }

    @Test
    void refresh_cuentaDesactivada_revocaTodasSusSesiones() {
        UserAccount ua = user(false, false);
        when(refreshTokens.validate("OLD")).thenReturn(Validation.ok(row(ua, Instant.now())));

        assertCode(() -> auth.refresh("OLD"), AuthException.UNAUTHORIZED);
        verify(refreshTokens).revokeAllForUser(ua.getId());
        verify(refreshTokens, never()).issue(any(), any(), any());
    }

    @Test
    void logout_revocaSoloSiHayToken() {
        auth.logout(null);
        verify(refreshTokens, never()).revoke(any());

        auth.logout("RT");
        verify(refreshTokens).revoke("RT");
    }
}
