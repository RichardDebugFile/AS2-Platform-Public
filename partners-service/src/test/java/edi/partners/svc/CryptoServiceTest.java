package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void cifraYDescifra_yCadaCifradoEsDistinto() {
        CryptoService crypto = new CryptoService(KEY);
        String a = crypto.encrypt("clave-del-socio");
        String b = crypto.encrypt("clave-del-socio");

        assertThat(a).isNotEqualTo(b).doesNotContain("clave-del-socio");
        assertThat(crypto.decrypt(a)).isEqualTo("clave-del-socio");
    }

    @Test
    void sinClave_usaUnaTemporal() {
        CryptoService crypto = new CryptoService("");
        assertThat(crypto.decrypt(crypto.encrypt("x"))).isEqualTo("x");
    }

    @Test
    void claveDeLongitudIncorrecta_falla() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CryptoService(corta)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cifradoAlterado_noSeDescifra() {
        CryptoService crypto = new CryptoService(KEY);
        assertThatThrownBy(() -> crypto.decrypt("bm8tZXMtdmFsaWRv")).isInstanceOf(IllegalStateException.class);
    }
}
