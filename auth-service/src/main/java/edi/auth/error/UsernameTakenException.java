package edi.auth.error;

/** Alta con un usuario que ya existe (sin distinguir mayusculas). Se responde 409. */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("El usuario '" + username + "' ya existe");
    }
}
