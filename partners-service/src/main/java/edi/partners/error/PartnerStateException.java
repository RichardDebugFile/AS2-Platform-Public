package edi.partners.error;

/** La operacion no corresponde a la etapa actual del socio (p. ej. activar produccion sin enlazar). */
public class PartnerStateException extends RuntimeException {

    public PartnerStateException(String message) {
        super(message);
    }
}
