package edi.partners.domain;

/** Etapa de las carpetas del socio (HU-09). */
public enum FolderState {
    /** Carpetas propias bajo la raiz de almacenamiento. */
    LOCAL,
    /** Cada subcarpeta es un enlace simbolico a la carpeta compartida del ERP. */
    SAMBA_LINKED,
    /** Enlazado y con la cola de entrada activa: el ERP recibe los documentos nuevos. */
    PRODUCTION
}
