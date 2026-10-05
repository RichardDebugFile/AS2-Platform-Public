package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static String randomKey(int bytes) {
        byte[] k = new byte[bytes];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private final String key = randomKey(32);

    @Test
    void cifrarYDescifrar_idaYVuelta() {
        CryptoService crypto = new CryptoService(key);

        String enc = crypto.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(enc).isNotEqualTo("JBSWY3DPEHPK3PXP").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(crypto.decrypt(enc)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void ivAleatorio_mismoTextoDaCifradosDistintos() {
        CryptoService crypto = new CryptoService(key);

        assertThat(crypto.encrypt("x")).isNotEqualTo(crypto.encrypt("x"));
    }

    @Test
    void otraClave_noPuedeDescifrar() {
        String enc = new CryptoService(key).encrypt("secreto");

        CryptoService otra = new CryptoService(randomKey(32));
        assertThatThrownBy(() -> otra.decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cifradoManipulado_seRechaza() {
        CryptoService crypto = new CryptoService(key);
        byte[] raw = Base64.getDecoder().decode(crypto.encrypt("secreto"));
        raw[raw.length - 1] ^= 1;
        String manipulado = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> crypto.decrypt(manipulado)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("corto")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claveDeLongitudIncorrecta_impideArrancar() {
        String corta = randomKey(16);

        assertThatThrownBy(() -> new CryptoService(corta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void sinClave_usaUnaTemporalQueFunciona() {
        CryptoService crypto = new CryptoService("");

        assertThat(crypto.decrypt(crypto.encrypt("dev"))).isEqualTo("dev");
    }
}
