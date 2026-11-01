package edi.documents.storage;

/** No se pudo escribir el documento en el repositorio en disco: la ingesta se revierte. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
