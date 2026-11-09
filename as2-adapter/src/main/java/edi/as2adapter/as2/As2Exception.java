package edi.as2adapter.as2;

/** Un mensaje AS2 o un MDN que no se puede armar o leer. */
public class As2Exception extends RuntimeException {

    public As2Exception(String message) {
        super(message);
    }

    public As2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
