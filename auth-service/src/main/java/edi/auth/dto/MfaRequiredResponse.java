package edi.auth.dto;

/**
 * Respuesta del login cuando la cuenta tiene MFA: la contrasena era correcta pero la sesion se abre
 * en {@code /auth/mfa/verify} con el codigo TOTP y este {@code mfaToken}.
 */
public record MfaRequiredResponse(String status, String mfaToken) {

    public static MfaRequiredResponse of(String mfaToken) {
        return new MfaRequiredResponse("MFA_REQUIRED", mfaToken);
    }
}
