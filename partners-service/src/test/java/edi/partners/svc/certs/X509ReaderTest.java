package edi.partners.svc.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.partners.testsupport.TestCertificates;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class X509ReaderTest {

    @Test
    void pemYDer_danLosMismosDatos() {
        byte[] der = TestCertificates.cert("as2.socio-a.example")
                .valid("2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z").der();

        X509Reader.Parsed fromDer = X509Reader.read(der);
        X509Reader.Parsed fromPem = X509Reader.read(TestCertificates.pem(der).getBytes(StandardCharsets.US_ASCII));

        assertThat(fromPem.data()).isEqualTo(fromDer.data());
        assertThat(fromDer.data().issuedTo()).isEqualTo("as2.socio-a.example");
        assertThat(fromDer.data().issuedBy()).isEqualTo("as2.socio-a.example");
        assertThat(fromDer.data().subjectDn()).contains("CN=as2.socio-a.example", "O=Socio de prueba");
        assertThat(fromDer.data().fingerprintSha256()).matches("[0-9a-f]{64}").isEqualTo(X509Reader.fingerprint(der));
        assertThat(fromDer.data().validFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(fromDer.data().validTo()).isEqualTo(Instant.parse("2028-01-01T00:00:00Z"));
        assertThat(fromDer.data().serialNumber()).matches("[0-9A-F]+");
    }

    @Test
    void pemNormalizado_conLineasDe64() {
        String pem = X509Reader.read(TestCertificates.cert("x").der()).data().pem();

        assertThat(pem).startsWith("-----BEGIN CERTIFICATE-----\n").endsWith("\n-----END CERTIFICATE-----\n");
        assertThat(pem.lines().skip(1).filter(l -> !l.startsWith("-----")))
                .allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(64));
    }

    @Test
    void pemConTextoAlrededorYFinDeLineaCrLf_seAcepta() {
        byte[] der = TestCertificates.cert("crlf").der();
        String pem = "Certificado del socio\r\n" + TestCertificates.pem(der).replace("\n", "\r\n");

        assertThat(X509Reader.read(pem.getBytes(StandardCharsets.US_ASCII)).data().fingerprintSha256())
                .isEqualTo(X509Reader.fingerprint(der));
    }

    @Test
    void usosDeLaClave() {
        assertThat(X509Reader.read(TestCertificates.cert("a").der()).allowsEncryption()).isTrue();
        assertThat(X509Reader.read(TestCertificates.cert("b").signatureOnly().der()).allowsEncryption()).isFalse();
        assertThat(X509Reader.read(TestCertificates.cert("c").signatureOnly().der()).allowsSignature()).isTrue();
        assertThat(X509Reader.read(TestCertificates.cert("d").noKeyUsage().der()).allowsEncryption()).isTrue();
        assertThat(X509Reader.read(TestCertificates.cert("e").ca().der()).isCa()).isTrue();
        assertThat(X509Reader.read(TestCertificates.cert("f").der()).isCa()).isFalse();
    }

    @Test
    void archivoIlegible_seRechaza() {
        assertThatThrownBy(() -> X509Reader.read("no es un certificado".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es un certificado X.509 valido");
        assertThatThrownBy(() -> X509Reader.read(new byte[0])).hasMessageContaining("vacio");
        assertThatThrownBy(() -> X509Reader.read(new byte[X509Reader.MAX_BYTES + 1])).hasMessageContaining("grande");
    }

    @Test
    void claveprivada_seRechaza() {
        String pem = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n"
                + TestCertificates.pem(TestCertificates.cert("k").der());

        assertThatThrownBy(() -> X509Reader.read(pem.getBytes(StandardCharsets.US_ASCII)))
                .hasMessageContaining("clave privada");
    }

    @Test
    void variosCertificados_seRechazan() {
        String chain = TestCertificates.pem(TestCertificates.cert("uno").der())
                + TestCertificates.pem(TestCertificates.cert("dos").der());

        assertThatThrownBy(() -> X509Reader.read(chain.getBytes(StandardCharsets.US_ASCII)))
                .hasMessageContaining("contiene 2 certificados");
    }

    @Test
    void nombreSinCn_seMuestraCompleto() {
        assertThat(X509Reader.commonName("O=Socio,C=EC")).isEqualTo("O=Socio,C=EC");
        assertThat(X509Reader.commonName("CN=uno,O=Socio")).isEqualTo("uno");
        assertThat(X509Reader.commonName("no es un DN")).isEqualTo("no es un DN");
    }
}
