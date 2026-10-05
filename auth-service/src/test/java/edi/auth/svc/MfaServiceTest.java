package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edi.auth.domain.UserAccount;
import edi.auth.dto.MfaSetupResponse;
import edi.auth.error.AuthException;
import edi.auth.error.MfaAlreadyEnabledException;
import edi.auth.repo.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Con TotpService y CryptoService reales: lo que se prueba es el ciclo completo del secreto. */
class MfaServiceTest {

    private UserAccountRepository users;
    private final TotpService totp = new TotpService("AS2 Platform");
    private final CryptoService crypto = new CryptoService("");
    private MfaService mfa;
    private UserAccount ua;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        mfa = new MfaService(users, totp, crypto);
        ua = new UserAccount();
        ua.setUsername("alice");
        when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(ua));
    }

    private String plainSecret() {
        return crypto.decrypt(ua.getMfaSecret());
    }

    @Test
    void setup_guardaElSecretoCifradoYSinActivar() {
        MfaSetupResponse r = mfa.setup("alice");

        assertThat(ua.isMfaEnabled()).isFalse();
        assertThat(ua.getMfaSecret()).isNotNull().isNotEqualTo(plainSecret());
        assertThat(r.otpauthUrl()).contains("secret=" + plainSecret());
        assertThat(r.secretMasked()).startsWith("****").hasSize(8);
        assertThat(r.qrPngBase64()).isNotBlank();
    }

    @Test
    void verificar_conCodigoCorrecto_activa() {
        mfa.setup("alice");

        mfa.enable("alice", totp.codeAt(plainSecret(), Instant.now()));

        assertThat(mfa.isEnabled("alice")).isTrue();
    }

    @Test
    void verificar_codigoIncorrectoOSinSetup_seRechaza() {
        assertThatThrownBy(() -> mfa.enable("alice", "123456")).isInstanceOf(IllegalArgumentException.class);

        mfa.setup("alice");
        String malo = totp.codeAt(plainSecret(), Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> mfa.enable("alice", malo)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ua.isMfaEnabled()).isFalse();
    }

    @Test
    void setup_conMfaActivo_seRechaza() {
        mfa.setup("alice");
        mfa.enable("alice", totp.codeAt(plainSecret(), Instant.now()));

        assertThatThrownBy(() -> mfa.setup("alice")).isInstanceOf(MfaAlreadyEnabledException.class);
    }

    @Test
    void desactivar_exigeCodigoValido() {
        mfa.setup("alice");
        mfa.enable("alice", totp.codeAt(plainSecret(), Instant.now()));

        assertThatThrownBy(() -> mfa.disable("alice", "000000")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ua.isMfaEnabled()).isTrue();

        mfa.disable("alice", totp.codeAt(plainSecret(), Instant.now()));
        assertThat(ua.isMfaEnabled()).isFalse();
        assertThat(ua.getMfaSecret()).isNull();
    }

    @Test
    void desactivar_altaAMedias_noPideCodigo() {
        mfa.setup("alice");

        mfa.disable("alice", "000000");

        assertThat(ua.getMfaSecret()).isNull();
    }

    @Test
    void cuentaInexistente_es401() {
        when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mfa.isEnabled("ghost")).isInstanceOf(AuthException.class);
    }
}
