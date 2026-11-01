package edi.partners.svc.certs;

import edi.partners.domain.PartnerCertificate.CertificateData;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

/**
 * Lee un certificado X.509 en PEM o DER (HU-11) con el JDK y extrae lo que se guarda y se muestra:
 * a quien y por quien fue emitido, numero de serie, huella SHA-256, vigencia y usos de la clave.
 * Rechaza los archivos que traen una clave privada (RN-14) o mas de un certificado.
 */
public final class X509Reader {

    /** Tamano maximo aceptado: un certificado ocupa unos pocos KB. */
    public static final int MAX_BYTES = 64 * 1024;

    private static final int KU_DIGITAL_SIGNATURE = 0;
    private static final int KU_NON_REPUDIATION = 1;
    private static final int KU_KEY_ENCIPHERMENT = 2;
    private static final int KU_DATA_ENCIPHERMENT = 3;

    private X509Reader() {
    }

    /** Certificado leido y sus datos. */
    public record Parsed(X509Certificate certificate, CertificateData data) {

        /** Es de una autoridad de certificacion (basicConstraints CA=true), no de entidad final. */
        public boolean isCa() {
            return certificate.getBasicConstraints() >= 0;
        }

        /** Sin extension keyUsage, cualquier uso esta permitido. */
        public boolean allowsEncryption() {
            boolean[] ku = certificate.getKeyUsage();
            return ku == null || ku[KU_KEY_ENCIPHERMENT] || ku[KU_DATA_ENCIPHERMENT];
        }

        public boolean allowsSignature() {
            boolean[] ku = certificate.getKeyUsage();
            return ku == null || ku[KU_DIGITAL_SIGNATURE] || ku[KU_NON_REPUDIATION];
        }
    }

    public static Parsed read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo es demasiado grande para ser un certificado");
        }
        String text = new String(content, StandardCharsets.ISO_8859_1);
        if (text.contains("PRIVATE KEY-----")) {
            throw new IllegalArgumentException(
                    "El archivo contiene una clave privada. Solo se cargan certificados (la parte publica) del socio");
        }
        Collection<? extends java.security.cert.Certificate> all;
        try {
            all = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(content));
        } catch (CertificateException e) {
            throw new IllegalArgumentException("El archivo no es un certificado X.509 valido (PEM o DER)", e);
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("El archivo no es un certificado X.509 valido (PEM o DER)");
        }
        if (all.size() > 1) {
            throw new IllegalArgumentException("El archivo contiene " + all.size()
                    + " certificados. Suba solo el certificado del socio, uno por archivo");
        }
        X509Certificate cert = (X509Certificate) all.iterator().next();
        return new Parsed(cert, data(cert));
    }

    private static CertificateData data(X509Certificate cert) {
        String subject = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);
        String issuer = cert.getIssuerX500Principal().getName(X500Principal.RFC2253);
        byte[] der = encoded(cert);
        return new CertificateData(commonName(subject), commonName(issuer), subject, issuer,
                cert.getSerialNumber().toString(16).toUpperCase(), fingerprint(der),
                cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant(), pem(der));
    }

    /** CN del nombre distinguido; si no tiene, el nombre completo. */
    static String commonName(String dn) {
        try {
            for (Rdn rdn : new LdapName(dn).getRdns().reversed()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return truncate(String.valueOf(rdn.getValue()));
                }
            }
        } catch (InvalidNameException e) {
            // Nombre no interpretable: se muestra completo.
        }
        return truncate(dn);
    }

    private static String truncate(String s) {
        return s.length() <= 255 ? s : s.substring(0, 255);
    }

    /** Huella SHA-256 del certificado codificado (DER), en hexadecimal en minusculas. */
    public static String fingerprint(byte[] der) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** PEM normalizado: lineas de 64 caracteres y fin de linea LF. */
    static String pem(byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
    }

    private static byte[] encoded(X509Certificate cert) {
        try {
            return cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("El certificado no se puede codificar", e);
        }
    }
}
