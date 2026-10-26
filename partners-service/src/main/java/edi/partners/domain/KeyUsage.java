package edi.partners.domain;

/** Para que se usa el certificado de un socio (HU-11). */
public enum KeyUsage {
    /** Cifrar lo que se le envia al socio. */
    ENCRYPTION,
    /** Verificar la firma de lo que el socio envia (y de sus MDN). */
    SIGNATURE,
    /** Confiar en el servidor HTTPS del socio. */
    SSL
}
