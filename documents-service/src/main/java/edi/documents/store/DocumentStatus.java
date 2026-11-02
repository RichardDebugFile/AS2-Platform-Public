package edi.documents.store;

/** Ciclo de vida de un documento en el repositorio. */
public enum DocumentStatus {
    CREATED,
    RECEIVED,
    SENT,
    SEND_FAILED,
    MDN_PENDING,
    MDN_RECEIVED,
    MDN_FAILED,
    VALIDATED,
    VALIDATION_FAILED,
    DELIVERED,
    DELIVERY_FAILED,
    PROCESSING_FAILED,
    DUPLICATE
}
