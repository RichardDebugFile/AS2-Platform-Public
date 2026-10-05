package edi.auth.error;

/** Setup de MFA con el MFA ya activo: rehacerlo lo apagaria sin pedir codigo. Se responde 409. */
public class MfaAlreadyEnabledException extends RuntimeException {

    public MfaAlreadyEnabledException() {
        super("El MFA ya está activo. Desactívalo primero con un código válido.");
    }
}
