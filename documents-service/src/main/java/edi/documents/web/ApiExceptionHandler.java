package edi.documents.web;

import edi.documents.ingest.DocumentNotFoundException;
import edi.documents.ingest.UnknownPartnerException;
import edi.documents.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores con cuerpo {@code {status, code, message}}, como el resto de servicios, para que la consola los explique. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BAD_REQUEST = "BAD_REQUEST";

    public record ApiError(int status, String code, String message) {
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ResponseEntity<ApiError> onUnsupported(UnsupportedDocumentTypeException e) {
        return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TYPE", e.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<ApiError> onNotFound(DocumentNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(UnknownPartnerException.class)
    ResponseEntity<ApiError> onUnknownPartner(UnknownPartnerException e) {
        return error(HttpStatus.BAD_REQUEST, "PARTNER_UNKNOWN", e.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ApiError> onStorage(StorageException e) {
        log.error("Ingesta revertida: {}", e.getMessage(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                e.getMessage() + ". No se registro el documento.");
    }

    /** Dos ingestas simultaneas del mismo Message-ID: la segunda pierde la carrera y puede reintentar. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onConflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_MESSAGE", "El Message-ID se esta registrando en otra peticion");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> onBadParameter(Exception e) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, "Parametro faltante o no valido: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> onInvalid(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, "El cuerpo de la peticion falta o no se puede leer");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> onMediaType(HttpMediaTypeNotSupportedException e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Tipo de contenido no admitido: los documentos van como text/plain y la generacion como JSON");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message));
    }
}
