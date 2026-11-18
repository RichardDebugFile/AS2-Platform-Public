package edi.partners.domain;

/** Direccion en que el socio intercambia un tipo de documento, vista desde la empresa. */
public enum DocumentDirection {
    /** El socio lo envia y la empresa lo recibe. */
    INBOUND,
    /** La empresa lo envia al socio. */
    OUTBOUND
}
