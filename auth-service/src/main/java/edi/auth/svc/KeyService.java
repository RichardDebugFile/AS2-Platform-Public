package edi.auth.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Par RSA con el que se firman los JWT. En servidor llega en PEM por variables de entorno; si faltan
 * se genera uno al arrancar (solo desarrollo: cada reinicio invalida las sesiones abiertas).
 */
@Service
public class KeyService {

    private static final Logger log = LoggerFactory.getLogger(KeyService.class);

    private final RSAKey rsaJwk;

    public KeyService(
            @Value("${auth.rsa.private-pem:}") String privatePem,
            @Value("${auth.rsa.public-pem:}") String publicPem) {
        this.rsaJwk = loadOrGenerate(privatePem, publicPem);
    }

    public RSAKey getRsaJwk() {
        return rsaJwk;
    }

    private static RSAKey loadOrGenerate(String privatePem, String publicPem) {
        boolean hayPrivada = privatePem != null && !privatePem.isBlank();
        boolean hayPublica = publicPem != null && !publicPem.isBlank();
        if (hayPrivada != hayPublica) {
            throw new IllegalStateException(
                    "Claves RSA incompletas: definir AUTH_JWT_PRIVATE_KEY_PEM y AUTH_JWT_PUBLIC_KEY_PEM juntas");
        }
        try {
            RSAPublicKey pub;
            RSAPrivateKey priv;
            if (hayPrivada) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pemToDer(publicPem)));
                priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pemToDer(privatePem)));
            } else {
                log.warn("Sin AUTH_JWT_*_KEY_PEM: se genera un par RSA temporal (solo desarrollo)");
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                pub = (RSAPublicKey) kp.getPublic();
                priv = (RSAPrivateKey) kp.getPrivate();
            }
            // kid derivado de la clave: estable entre reinicios (los validadores cachean el JWKS por kid).
            return new RSAKey.Builder(pub).privateKey(priv).keyIDFromThumbprint().build();
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudieron inicializar las claves RSA", e);
        }
    }

    static byte[] pemToDer(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
