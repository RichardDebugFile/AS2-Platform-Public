package edi.documents.store;

/** Entrada: lo envio el socio. Salida: lo enviamos nosotros. */
public enum Direction {
    INBOUND,
    OUTBOUND;

    /** Carpeta del repositorio en disco. */
    public String folder() {
        return this == INBOUND ? "inbound" : "outbound";
    }
}
