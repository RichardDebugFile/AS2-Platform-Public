package edi.partners.error;

/** El catalogo no admite el cambio: tipo repetido o tipo en uso por algun socio (409). */
public class DocumentTypeConflictException extends RuntimeException {

    private final String code;

    public DocumentTypeConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
