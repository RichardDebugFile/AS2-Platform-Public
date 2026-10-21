package edi.partners.error;

public class DuplicateAs2NameException extends RuntimeException {

    public DuplicateAs2NameException(String as2Name) {
        super("Ya existe un socio activo con el identificador AS2 '" + as2Name + "'");
    }
}
