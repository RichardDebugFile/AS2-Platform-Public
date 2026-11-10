package edi.auth.error;

/**
 * Un administrador intenta desactivar su propia cuenta: perderia el acceso en ese momento y, si es el
 * unico ADMIN, nadie podria reactivarlo desde la consola. Se responde 409.
 */
public class SelfDeactivationException extends RuntimeException {

    public SelfDeactivationException() {
        super("No puede desactivar su propia cuenta");
    }
}
