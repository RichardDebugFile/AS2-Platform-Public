package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MimeTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void partArmaCabecerasLineaEnBlancoYContenido() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/plain");
        h.put("X-Uno", "1");
        assertThat(new String(Mime.part(h, b("hola")), StandardCharsets.US_ASCII))
                .isEqualTo("Content-Type: text/plain\r\nX-Uno: 1\r\n\r\nhola");
    }

    @Test
    void splitDevuelveLasPartesExactasSinElSaltoDelDelimitador() {
        byte[] body = b("preambulo\r\n--B\r\nA: 1\r\n\r\nuno\r\n--B\r\n\r\ndos\r\n\r\n--B--\r\nepilogo");
        List<byte[]> parts = Mime.split(body, "B");
        assertThat(parts).hasSize(2);
        assertThat(new String(parts.get(0), StandardCharsets.ISO_8859_1)).isEqualTo("A: 1\r\n\r\nuno");
        assertThat(new String(parts.get(1), StandardCharsets.ISO_8859_1)).isEqualTo("\r\ndos\r\n");
    }

    @Test
    void splitRechazaUnMultipartSinDelimitadorOSinCierre() {
        assertThatThrownBy(() -> Mime.split(b("nada"), "B")).isInstanceOf(As2Exception.class);
        assertThatThrownBy(() -> Mime.split(b("--B\r\nA: 1\r\n\r\nsin cierre"), "B")).isInstanceOf(As2Exception.class);
    }

    @Test
    void headersUneLineasPlegadasYUbicaElContenido() {
        byte[] part = b("Content-Type: multipart/report;\r\n\treport-type=disposition-notification\r\nX: y\r\n\r\ncuerpo");
        Mime.Headers h = Mime.headers(part);
        assertThat(h.get("CONTENT-TYPE")).isEqualTo("multipart/report; report-type=disposition-notification");
        assertThat(new String(h.body(part), StandardCharsets.ISO_8859_1)).isEqualTo("cuerpo");

        byte[] sinCabeceras = b("\r\nsolo cuerpo");
        assertThat(new String(Mime.headers(sinCabeceras).body(sinCabeceras), StandardCharsets.ISO_8859_1))
                .isEqualTo("solo cuerpo");
        assertThatThrownBy(() -> Mime.headers(b("A: 1"))).isInstanceOf(As2Exception.class);
    }

    @Test
    void decodedBodyDecodificaBase64() {
        byte[] part = b("Content-Transfer-Encoding: base64\r\n\r\naG9s\r\nYQ==");
        assertThat(new String(Mime.headers(part).decodedBody(part), StandardCharsets.US_ASCII)).isEqualTo("hola");
    }

    @Test
    void paramLeeValoresConYSinComillas() {
        String ct = "multipart/signed; protocol=\"application/pkcs7-signature\"; MICALG=sha-256; boundary=\"a;b\"";
        assertThat(Mime.param(ct, "boundary")).isEqualTo("a;b");
        assertThat(Mime.param(ct, "micalg")).isEqualTo("sha-256");
        assertThat(Mime.param(ct, "nada")).isNull();
        assertThat(Mime.param(null, "boundary")).isNull();
        assertThat(Mime.mediaType(" Multipart/Signed ; x=1")).isEqualTo("multipart/signed");
        assertThat(Mime.mediaType(null)).isEmpty();
    }

    @Test
    void boundaryEsUnicoYBase64UsaLineasDe76() {
        assertThat(Mime.boundary()).isNotEqualTo(Mime.boundary());
        String lines = new String(Mime.base64Lines(new byte[100]), StandardCharsets.US_ASCII);
        assertThat(lines.split("\r\n")[0]).hasSize(76);
    }
}
