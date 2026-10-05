package edi.auth.error;

import java.util.UUID;

/** Usuario inexistente en una operacion de administracion. Se responde 404. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Usuario no encontrado: " + id);
    }
}
