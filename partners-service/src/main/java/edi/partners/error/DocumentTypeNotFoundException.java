package edi.partners.error;

import java.util.UUID;

public class DocumentTypeNotFoundException extends RuntimeException {

    public DocumentTypeNotFoundException(UUID id) {
        super("Tipo de documento no encontrado: " + id);
    }
}
