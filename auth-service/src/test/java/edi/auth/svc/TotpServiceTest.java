package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    /** Semilla del anexo B de la RFC 6238 ("12345678901234567890"), en Base32 como la guarda el servicio. */
    private static final String RFC_SECRET =
            new Base32().encodeToString("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    private final TotpService totp = new TotpService("AS2 Platform");

    @Test
    void vectoresDeLaRfc6238() {
        // La RFC da 8 digitos; los 6 de la derecha son el codigo de 6 digitos.
        assertThat(totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThat(totp.codeAt(RFC_SECRET, Instant.ofEpochSecond(1234567890))).isEqualTo("005924");
    }

    @Test
    void verificar_admiteUnPasoDeDesfase() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String anterior = totp.codeAt(RFC_SECRET, now.minusSeconds(30));
        String siguiente = totp.codeAt(RFC_SECRET, now.plusSeconds(30));
        String lejano = totp.codeAt(RFC_SECRET, now.minusSeconds(90));

        assertThat(totp.verifyCode(RFC_SECRET, totp.codeAt(RFC_SECRET, now), now)).isTrue();
        assertThat(totp.verifyCode(RFC_SECRET, anterior, now)).isTrue();
        assertThat(totp.verifyCode(RFC_SECRET, siguiente, now)).isTrue();
        assertThat(totp.verifyCode(RFC_SECRET, lejano, now)).isFalse();
    }

    @Test
    void verificar_rechazaFormatosInvalidos() {
        assertThat(totp.verifyCode(RFC_SECRET, null)).isFalse();
        assertThat(totp.verifyCode(RFC_SECRET, "12345")).isFalse();
        assertThat(totp.verifyCode(RFC_SECRET, "abcdef")).isFalse();
    }

    @Test
    void secreto_esBase32De160Bits() {
        String s = totp.generateBase32Secret();

        assertThat(s).hasSize(32).matches("[A-Z2-7]+");
        assertThat(totp.generateBase32Secret()).isNotEqualTo(s);
    }

    @Test
    void uriOtpauth_llevaEmisorUsuarioYSecreto() {
        String url = totp.buildOtpAuthUrl("ana lopez", RFC_SECRET);

        assertThat(url)
                .startsWith("otpauth://totp/AS2%20Platform:ana%20lopez?")
                .contains("secret=" + RFC_SECRET)
                .contains("issuer=AS2%20Platform")
                .contains("digits=6")
                .contains("period=30");
    }

    @Test
    void qr_esUnPng() {
        byte[] png = Base64.getDecoder().decode(totp.qrPngBase64("otpauth://totp/x?secret=" + RFC_SECRET));

        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
    }
}
