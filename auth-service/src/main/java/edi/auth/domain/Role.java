package edi.auth.domain;

/** Roles de la consola (minimo privilegio). */
public enum Role {
    /** Acceso completo, incluida la gestion de usuarios, SMTP y certificados. */
    ADMIN,
    /** Opera socios, documentos y envios. */
    OPERATOR,
    /** Solo lectura. */
    AUDITOR
}
