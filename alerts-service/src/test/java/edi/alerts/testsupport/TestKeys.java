package edi.alerts.testsupport;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Claves AES-256 de prueba, generadas al correr: en el repositorio no se escribe ninguna clave, ni
 * siquiera de prueba (gitleaks las trataria como filtradas).
 */
public final class TestKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestKeys() {
    }

    /** 32 bytes aleatorios en Base64, el formato de ALERTS_SECRET_KEY. */
    public static String aes256() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
