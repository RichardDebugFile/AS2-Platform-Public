package edi.partners.domain;

/** Content-Type del documento que viaja en el mensaje AS2. */
public enum MimeType {
    APPLICATION_EDI_X12("application/edi-x12"),
    APPLICATION_EDIFACT("application/edifact"),
    APPLICATION_XML("application/xml"),
    TEXT_PLAIN("text/plain");

    private final String contentType;

    MimeType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }
}
