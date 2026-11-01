package edi.partners.error;

import java.util.UUID;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(UUID id) {
        super("Certificado no encontrado: " + id);
    }
}
