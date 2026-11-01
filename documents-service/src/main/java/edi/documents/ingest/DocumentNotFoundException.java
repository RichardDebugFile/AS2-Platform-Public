package edi.documents.ingest;

/** No existe el documento (o su MDN) pedido. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
