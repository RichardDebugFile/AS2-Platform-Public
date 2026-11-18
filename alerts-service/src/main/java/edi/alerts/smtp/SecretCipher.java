package edi.alerts.smtp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra en reposo la contrasena SMTP con AES-256-GCM. Formato: {@code gcm:} + Base64(IV de 12 bytes ||
 * cifrado+tag); el prefijo dice con que se cifro el valor guardado.
 *
 * <p>La clave llega en {@code ALERTS_SECRET_KEY} (32 bytes en Base64). Sin ella se usa una clave temporal:
 * sirve para desarrollo, pero la contrasena guardada deja de poder leerse al reiniciar.</p>
 */
@Component
public class SecretCipher {

    static final String PREFIX = "gcm:";
    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String UNREADABLE =
            "No se pudo descifrar la contrasena SMTP (cambio la clave ALERTS_SECRET_KEY?): vuelva a escribirla";

    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public SecretCipher(@Value("${alerts.secret-key:}") String base64Key) {
        this.key = new SecretKeySpec(keyBytes(base64Key, random), "AES");
    }

    private static byte[] keyBytes(String base64Key, SecureRandom random) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("Sin ALERTS_SECRET_KEY: se usa una clave temporal (solo desarrollo)");
            byte[] temporary = new byte[KEY_BYTES];
            random.nextBytes(temporary);
            return temporary;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("ALERTS_SECRET_KEY debe ser de 32 bytes en Base64");
        }
        return raw;
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        byte[] sealed = apply(Cipher.ENCRYPT_MODE, iv, plain.getBytes(StandardCharsets.UTF_8));
        byte[] stored = Arrays.copyOf(iv, IV_BYTES + sealed.length);
        System.arraycopy(sealed, 0, stored, IV_BYTES, sealed.length);
        return PREFIX + Base64.getEncoder().encodeToString(stored);
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("La contrasena guardada no tiene un formato conocido");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) {
                throw new IllegalArgumentException("valor cifrado incompleto");
            }
            byte[] plain = apply(Cipher.DECRYPT_MODE, Arrays.copyOfRange(all, 0, IV_BYTES),
                    Arrays.copyOfRange(all, IV_BYTES, all.length));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException(UNREADABLE, e);
        }
    }

    /** Una sola operacion AES-GCM; un tag que no coincide (otra clave, dato alterado) es un error. */
    private byte[] apply(int mode, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM no pudo procesar el valor", e);
        }
    }
}
