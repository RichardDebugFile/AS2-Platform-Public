package edi.as2adapter.as2;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.crypto.RuntimeCryptoException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;

/**
 * Operaciones CMS (RFC 5652) de AS2: firma separada, cifrado para el socio, descifrado y verificacion,
 * y el calculo del MIC que el socio devuelve en el MDN.
 */
public final class Cms {

    static final Provider BC = new BouncyCastleProvider();

    private Cms() {
    }

    /** Firma separada (detached) SHA-256 con RSA, con el certificado del firmante incluido. */
    public static byte[] sign(byte[] content, As2Identity signer) {
        try {
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider(BC)
                    .build("SHA256withRSA", signer.key(), signer.certificate()));
            gen.addCertificates(new JcaCertStore(List.of(signer.certificate())));
            return gen.generate(new CMSProcessableByteArray(content), false).getEncoded();
        } catch (CMSException | OperatorCreationException | IOException | java.security.cert.CertificateEncodingException e) {
            throw new As2Exception("No se pudo firmar el mensaje: " + e.getMessage(), e);
        }
    }

    /** True si la firma separada corresponde a {@code content} y la hizo la clave de {@code signer}. */
    public static boolean verify(byte[] content, byte[] signature, X509Certificate signer) {
        try {
            CMSSignedData signed = new CMSSignedData(new CMSProcessableByteArray(content), signature);
            Collection<SignerInformation> signers = signed.getSignerInfos().getSigners();
            if (signers.isEmpty()) return false;
            for (SignerInformation si : signers) {
                if (!si.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BC).build(signer))) return false;
            }
            return true;
        } catch (CMSException | OperatorCreationException _) {
            return false;
        }
    }

    /** Cifra con AES-256-CBC para el certificado del destinatario (enveloped-data). */
    public static byte[] encrypt(byte[] content, X509Certificate recipient) {
        try {
            CMSEnvelopedDataGenerator gen = new CMSEnvelopedDataGenerator();
            gen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipient).setProvider(BC));
            return gen.generate(new CMSProcessableByteArray(content),
                    new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider(BC).build()).getEncoded();
        } catch (CMSException | java.security.cert.CertificateEncodingException | IOException e) {
            throw new As2Exception("No se pudo cifrar el mensaje: " + e.getMessage(), e);
        }
    }

    /** Descifra un enveloped-data dirigido a {@code identity}. */
    public static byte[] decrypt(byte[] enveloped, As2Identity identity) {
        return decrypt(enveloped, identity.key());
    }

    static byte[] decrypt(byte[] enveloped, PrivateKey key) {
        try {
            CMSEnvelopedData env = new CMSEnvelopedData(ContentInfo.getInstance(enveloped));
            Collection<RecipientInformation> recipients = env.getRecipientInfos().getRecipients();
            if (recipients.isEmpty()) throw new As2Exception("El mensaje cifrado no tiene destinatarios");
            return recipients.iterator().next().getContent(new JceKeyTransEnvelopedRecipient(key).setProvider(BC));
        } catch (CMSException | IllegalArgumentException | RuntimeCryptoException e) {
            // Con una clave equivocada, segun el valor cifrado, falla como CMSException o como
            // DataLengthException ("input too large for RSA cipher"): las dos son "no se pudo descifrar".
            throw new As2Exception("No se pudo descifrar el mensaje: " + e.getMessage(), e);
        }
    }

    /**
     * MIC (RFC 4130 7.3.1): resumen en base64 de la parte firmada, cabeceras incluidas. El socio lo
     * calcula al recibir y lo devuelve en el MDN; si coincide, recibio exactamente lo que se envio.
     */
    public static String mic(byte[] signedPart, String algorithm) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance(jcaDigest(algorithm)).digest(signedPart));
        } catch (NoSuchAlgorithmException e) {
            throw new As2Exception("Algoritmo de MIC no soportado: " + algorithm, e);
        }
    }

    /** {@code sha-256} (nombre MIME, RFC 5751) a {@code SHA-256} (nombre del JDK). */
    static String jcaDigest(String micalg) {
        String a = micalg.strip().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "sha1", "sha-1" -> "SHA-1";
            case "sha256", "sha-256" -> "SHA-256";
            case "sha384", "sha-384" -> "SHA-384";
            case "sha512", "sha-512" -> "SHA-512";
            default -> throw new As2Exception("Algoritmo de MIC no soportado: " + micalg);
        };
    }
}
