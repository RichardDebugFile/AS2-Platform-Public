package edi.partners.domain;

/** Como devuelve el acuse (MDN): en la misma conexion HTTP o en una conexion posterior. */
public enum MdnMode {
    SYNC,
    ASYNC
}
