package edi.partners.testsupport;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Certificados X.509 autofirmados para las pruebas (HU-11), con datos ficticios. Nunca se guardan
 * en el repositorio: se generan en cada ejecucion.
 */
public final class TestCertificates {

    private static final AtomicLong SERIAL = new AtomicLong(1000);
    private static final KeyPair KEYS = keyPair();

    private TestCertificates() {
    }

    /** Opciones del certificado: por defecto, entidad final que permite cifrar y firmar. */
    public static Builder cert(String commonName) {
        return new Builder(commonName);
    }

    public static final class Builder {
        private final String commonName;
        // Por defecto vigente hoy y por dos anos, para que las pruebas no caduquen con el calendario.
        private Instant from = Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
        private Instant to = Instant.now().plus(Duration.ofDays(730)).truncatedTo(ChronoUnit.SECONDS);
        private boolean ca;
        private Integer keyUsage = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;

        private Builder(String commonName) {
            this.commonName = commonName;
        }

        public Builder valid(String from, String to) {
            this.from = Instant.parse(from);
            this.to = Instant.parse(to);
            return this;
        }

        public Builder ca() {
            this.ca = true;
            this.keyUsage = KeyUsage.keyCertSign | KeyUsage.cRLSign;
            return this;
        }

        public Builder signatureOnly() {
            this.keyUsage = KeyUsage.digitalSignature;
            return this;
        }

        public Builder noKeyUsage() {
            this.keyUsage = null;
            return this;
        }

        public byte[] der() {
            try {
                X500Name name = new X500Name("CN=" + commonName + ",O=Socio de prueba,C=EC");
                JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(name,
                        BigInteger.valueOf(SERIAL.incrementAndGet()), Date.from(from), Date.from(to), name,
                        KEYS.getPublic());
                b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
                if (keyUsage != null) {
                    b.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
                }
                ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(KEYS.getPrivate());
                X509CertificateHolder holder = b.build(signer);
                return holder.getEncoded();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public byte[] pem() {
            return TestCertificates.pem(der()).getBytes(StandardCharsets.US_ASCII);
        }
    }

    public static String pem(byte[] der) {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
