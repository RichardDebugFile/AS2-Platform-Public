package edi.auth.svc;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cifra el secreto TOTP en reposo con AES-256-GCM. Formato: Base64(IV de 12 bytes || cifrado+tag).
 *
 * <p>La clave llega en {@code AUTH_MFA_SECRET_KEY} (32 bytes en Base64). Sin ella se usa una clave
 * temporal: sirve para desarrollo, pero los MFA dados de alta dejan de poder verificarse al
 * reiniciar.</p>
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LEN = 32;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${auth.mfa.secret-key:}") String base64Key) {
        byte[] raw;
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("Sin AUTH_MFA_SECRET_KEY: se usa una clave temporal (solo desarrollo)");
            raw = new byte[KEY_LEN];
            random.nextBytes(raw);
        } else {
            raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != KEY_LEN) {
                throw new IllegalStateException("AUTH_MFA_SECRET_KEY debe ser de 32 bytes en Base64");
            }
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(IV_LEN + enc.length).put(iv).put(enc).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error cifrando", e);
        }
    }

    public String decrypt(String cipherB64) {
        try {
            ByteBuffer all = ByteBuffer.wrap(Base64.getDecoder().decode(cipherB64));
            byte[] iv = new byte[IV_LEN];
            all.get(iv);
            byte[] enc = new byte[all.remaining()];
            all.get(enc);
            Cipher c = Cipher.getInstance(TRANSFORMATION);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | BufferUnderflowException e) {
            throw new IllegalStateException("Error descifrando", e);
        }
    }
}
