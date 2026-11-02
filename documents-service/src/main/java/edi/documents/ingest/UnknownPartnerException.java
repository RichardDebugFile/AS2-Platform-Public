package edi.documents.ingest;

/** El socio no tiene carpeta: no esta registrado en partners-service (HU-08/HU-09). */
public class UnknownPartnerException extends RuntimeException {

    public UnknownPartnerException(String as2Name) {
        super("El socio '" + as2Name + "' no esta registrado (no tiene carpeta)");
    }
}
