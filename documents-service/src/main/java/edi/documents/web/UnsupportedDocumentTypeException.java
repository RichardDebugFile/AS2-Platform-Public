package edi.documents.web;

/** Se pidio generar un tipo de documento que el generador no conoce. */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String type) {
        super("Tipo de documento no admitido: '" + type + "'. Admitidos: 850, 997 y 889");
    }
}
