package edi.partners.error;

import java.util.UUID;

public class PartnerNotFoundException extends RuntimeException {

    public PartnerNotFoundException(UUID id) {
        super("Socio no encontrado: " + id);
    }

    public PartnerNotFoundException(String as2Name) {
        super("No hay un socio activo con identificador AS2 '" + as2Name + "'");
    }
}
