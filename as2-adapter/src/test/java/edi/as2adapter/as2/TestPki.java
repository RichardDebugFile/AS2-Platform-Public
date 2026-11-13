package edi.as2adapter.as2;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Identidades AS2 de prueba (clave RSA y certificado autofirmado con datos ficticios). Se generan en
 * cada ejecucion: ningun almacen de claves se guarda en el repositorio.
 */
public final class TestPki {

    private static final AtomicLong SERIAL = new AtomicLong(5000);

    private TestPki() {
    }

    public static As2Identity identity(String commonName) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            X500Name name = new X500Name("CN=" + commonName + ",O=Prueba AS2,C=EC");
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(name,
                    BigInteger.valueOf(SERIAL.incrementAndGet()), Date.from(now.minus(Duration.ofDays(1))),
                    Date.from(now.plus(Duration.ofDays(365))), name, kp.getPublic());
            b.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            X509Certificate cert = new JcaX509CertificateConverter()
                    .getCertificate(b.build(new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate())));
            return new As2Identity(cert, kp.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Guarda la identidad en un PKCS#12 temporal y devuelve su ruta. */
    public static Path pkcs12(As2Identity id, Path dir, String alias, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(alias, id.key(), password, new Certificate[] {id.certificate()});
        Path file = dir.resolve(alias + ".p12");
        try (OutputStream out = Files.newOutputStream(file)) {
            ks.store(out, password);
        }
        return file;
    }

    /** Guarda el certificado en DER y devuelve su ruta. */
    public static Path der(X509Certificate cert, Path dir, String name) throws Exception {
        return Files.write(dir.resolve(name + ".cer"), cert.getEncoded());
    }
}
