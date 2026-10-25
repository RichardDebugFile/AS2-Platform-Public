package edi.partners.domain;

/** Protocolo con el que se llega al servidor AS2 del socio. */
public enum TransportType {
    HTTP,
    HTTPS;

    public String scheme() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
