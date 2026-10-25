package edi.partners.svc.folders;

/** Fallo al preparar las carpetas de un socio. */
public class PartnerFolderException extends RuntimeException {

    public PartnerFolderException(String message, Throwable cause) {
        super(message, cause);
    }

    public PartnerFolderException(String message) {
        super(message);
    }
}
