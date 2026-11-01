package edi.documents.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores con cuerpo {@code {status, code, message}}, como el resto de servicios, para que la consola los explique. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String BAD_REQUEST = "BAD_REQUEST";

    public record ApiError(int status, String code, String message) {
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    ResponseEntity<ApiError> onUnsupported(UnsupportedDocumentTypeException e) {
        return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TYPE", e.getMessage());
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
