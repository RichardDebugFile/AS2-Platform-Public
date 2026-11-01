package edi.documents.store;

/** Copia del documento en la carpeta del socio (la que lee el ERP). */
public enum PartnerCopyStatus {
    /** La copia esta en la carpeta del socio. */
    COPIED,
    /** No se pudo copiar (p. ej. la carpeta compartida estaba caida): la recuperacion programada la reintenta. */
    PENDING
}
