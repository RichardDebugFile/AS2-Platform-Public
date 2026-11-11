package edi.as2adapter.as2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;

/**
 * Clave privada y certificado propios con los que se firma lo que sale y se descifra lo que llega.
 * Se leen de un almacen PKCS#12; la clave nunca sale de este servicio.
 */
public record As2Identity(X509Certificate certificate, PrivateKey key) {

    /** Lee la entrada {@code alias} (o la unica con clave, si alias es nulo) de un PKCS#12. */
    public static As2Identity fromPkcs12(Path file, char[] password, String alias) {
        try (InputStream in = Files.newInputStream(file)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password);
            String a = alias != null && !alias.isBlank() ? alias : firstKeyAlias(ks);
            if (!(ks.getKey(a, password) instanceof PrivateKey key)) {
                throw new As2Exception("El almacen " + file.getFileName() + " no tiene una clave privada con el alias " + a);
            }
            return new As2Identity((X509Certificate) ks.getCertificate(a), key);
        } catch (IOException | GeneralSecurityException e) {
            throw new As2Exception("No se pudo leer el almacen " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /** Certificado del socio (PEM o DER), con el que se cifra y se verifica su MDN. */
    public static X509Certificate certificate(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (IOException | GeneralSecurityException e) {
            throw new As2Exception("No se pudo leer el certificado " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static String firstKeyAlias(KeyStore ks) throws GeneralSecurityException {
        for (String a : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(a)) return a;
        }
        throw new As2Exception("El almacen no contiene ninguna clave privada");
    }
}
