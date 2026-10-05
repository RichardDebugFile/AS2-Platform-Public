package edi.auth.error;

/**
 * Fallo de autenticacion (401). El {@code code} permite a la consola explicar el motivo real:
 * {@code SESSION_EXPIRED} e {@code IDLE_EXPIRED} no se muestran igual que unas credenciales malas.
 */
public class AuthException extends RuntimeException {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String IDLE_EXPIRED = "IDLE_EXPIRED";
    public static final String MFA_CHALLENGE_EXPIRED = "MFA_CHALLENGE_EXPIRED";
    public static final String MFA_INVALID_CODE = "MFA_INVALID_CODE";
    public static final String MFA_ATTEMPTS_EXCEEDED = "MFA_ATTEMPTS_EXCEEDED";

    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static AuthException badCredentials() {
        // Mismo mensaje para usuario inexistente, inactivo o contrasena erronea: no revela cuentas.
        return new AuthException(UNAUTHORIZED, "Usuario o contraseña inválidos");
    }

    /** Token valido de una cuenta que ya no existe. */
    public static AuthException accountGone() {
        return new AuthException(UNAUTHORIZED, "La cuenta de la sesión ya no existe");
    }

    public String getCode() {
        return code;
    }
}
