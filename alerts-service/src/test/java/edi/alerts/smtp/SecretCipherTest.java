package edi.alerts.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY_A = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String KEY_B = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    @Test
    void cifra_conPrefijoGcm_yUnIvDistintoCadaVez() {
        SecretCipher c = new SecretCipher(KEY_A);
        String a = c.encrypt("contraseña-smtp");
        String b = c.encrypt("contraseña-smtp");

        assertThat(a).startsWith("gcm:").isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo("contraseña-smtp");
        assertThat(c.decrypt(b)).isEqualTo("contraseña-smtp");
    }

    @Test
    void conOtraClave_oSinPrefijo_noDescifraYLoExplica() {
        String stored = new SecretCipher(KEY_A).encrypt("x");

        assertThatThrownBy(() -> new SecretCipher(KEY_B).decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vuelva a escribirla");
        assertThatThrownBy(() -> new SecretCipher(KEY_A).decrypt("texto-plano"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claveDeLongitudIncorrecta_seRechazaAlArrancar() {
        assertThatThrownBy(() -> new SecretCipher("YWJj"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void laCausaEsLaMasConcreta() {
        Exception e = new RuntimeException("Mail server connection failed",
                new IllegalStateException("Couldn't connect to host", new java.net.ConnectException("Connection refused")));
        assertThat(SmtpConfigService.cause(e)).isEqualTo("Connection refused");
    }
}
