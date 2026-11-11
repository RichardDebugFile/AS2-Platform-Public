package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MdnReaderTest {

    private static final As2Identity SOCIO = MessageBuilderTest.SOCIO;
    private static final String PROCESSED = "automatic-action/MDN-sent-automatically; processed";

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void leeUnMdnFirmadoProcesadoConSuMic() {
        String[] mdn = new FakePartner(SOCIO).mdn("<m1@plataforma>", PROCESSED, "QUJD");

        Mdn r = MdnReader.read(mdn[0], b(mdn[1]), SOCIO.certificate());

        assertThat(r.signed()).isTrue();
        assertThat(r.signatureValid()).isTrue();
        assertThat(r.processed()).isTrue();
        assertThat(r.originalMessageId()).isEqualTo("<m1@plataforma>");
        assertThat(r.receivedContentMic()).isEqualTo("QUJD");
        assertThat(r.micAlgorithm()).isEqualTo("sha-256");
        assertThat(r.micMatches("QUJD", "SHA256")).isTrue();
        assertThat(r.micMatches("OTRO", "sha-256")).isFalse();
        assertThat(r.text()).isEqualTo("El mensaje fue recibido.");
    }

    @Test
    void unaFirmaDeOtroCertificadoOUnMdnAlteradoNoSonValidos() {
        String[] mdn = new FakePartner(SOCIO).mdn("<m1@plataforma>", PROCESSED, "QUJD");

        assertThat(MdnReader.read(mdn[0], b(mdn[1]), TestPki.identity("otro.example").certificate()).signatureValid())
                .isFalse();
        String alterado = mdn[1].replace("processed", "processexx");
        assertThat(MdnReader.read(mdn[0], b(alterado), SOCIO.certificate()).signatureValid()).isFalse();
    }

    @Test
    void unaDisposicionConErrorNoEsProcesada() {
        String[] mdn = new FakePartner(SOCIO).mdn("<m1@plataforma>",
                "automatic-action/MDN-sent-automatically; processed/error: decryption-failed", null);

        Mdn r = MdnReader.read(mdn[0], b(mdn[1]), SOCIO.certificate());

        assertThat(r.processed()).isFalse();
        assertThat(r.receivedContentMic()).isNull();
        assertThat(r.micMatches("QUJD", "sha-256")).isFalse();
        assertThat(new Mdn(null, null, null, null, false, false, null).processed()).isFalse();
        assertThat(new Mdn(null, "processed", null, null, false, false, null).processed()).isTrue();
    }

    @Test
    void leeUnMdnSinFirmaConCamposEnLineasLf() {
        // Los campos del reporte terminan en LF; el resto, en CRLF (\r al final de la linea).
        String body = """
                --R\r
                Content-Type: message/disposition-notification\r
                \r
                Original-Message-ID: <m2@plataforma>
                Disposition: automatic-action/MDN-sent-automatically; processed
                Received-Content-MIC: QUJD\r
                --R--\r
                """;

        Mdn r = MdnReader.read("multipart/report; report-type=disposition-notification; boundary=R", b(body),
                SOCIO.certificate());

        assertThat(r.signed()).isFalse();
        assertThat(r.processed()).isTrue();
        assertThat(r.originalMessageId()).isEqualTo("<m2@plataforma>");
        assertThat(r.receivedContentMic()).isEqualTo("QUJD");
        assertThat(r.micAlgorithm()).isNull();
        assertThat(r.micMatches("QUJD", "sha-256")).isFalse();
    }

    @Test
    void rechazaRespuestasQueNoSonUnMdn() {
        assertThatThrownBy(() -> MdnReader.read("text/html", b("<html/>"), SOCIO.certificate()))
                .isInstanceOf(As2Exception.class).hasMessageContaining("no es un MDN");
        assertThatThrownBy(() -> MdnReader.read("multipart/report", b("--R\r\n\r\nx\r\n--R--"), SOCIO.certificate()))
                .isInstanceOf(As2Exception.class).hasMessageContaining("boundary");
        assertThatThrownBy(() -> MdnReader.read("multipart/report; boundary=R",
                b("--R\r\nContent-Type: text/plain\r\n\r\nsolo texto\r\n--R--\r\n"), SOCIO.certificate()))
                .isInstanceOf(As2Exception.class).hasMessageContaining("disposition-notification");
        assertThatThrownBy(() -> MdnReader.read("multipart/signed; boundary=S",
                b("--S\r\nContent-Type: text/plain\r\n\r\nx\r\n--S--\r\n"), SOCIO.certificate()))
                .isInstanceOf(As2Exception.class).hasMessageContaining("firma");
        assertThatThrownBy(() -> MdnReader.read("multipart/signed; boundary=S",
                b("--S\r\nContent-Type: text/plain\r\n\r\nx\r\n--S\r\nContent-Type: application/pkcs7-signature\r\n\r\nAA==\r\n--S--\r\n"),
                SOCIO.certificate()))
                .isInstanceOf(As2Exception.class).hasMessageContaining("multipart/report");
    }

    @Test
    void algoritmosDeMicConocidos() {
        assertThat(Cms.jcaDigest("SHA1")).isEqualTo("SHA-1");
        assertThat(Cms.jcaDigest("sha-384")).isEqualTo("SHA-384");
        assertThat(Cms.jcaDigest("sha512")).isEqualTo("SHA-512");
        assertThatThrownBy(() -> Cms.jcaDigest("md5")).isInstanceOf(As2Exception.class);
        assertThatThrownBy(() -> Cms.mic(new byte[0], "md5")).isInstanceOf(As2Exception.class);
    }
}
