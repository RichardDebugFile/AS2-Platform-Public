package edi.alerts.web;

import edi.alerts.smtp.SmtpNotConfiguredException;
import edi.alerts.smtp.SmtpSendException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores con cuerpo {@code {status, code, message}}, como el resto de servicios, para que la consola los explique. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BAD_REQUEST = "BAD_REQUEST";

    public record ApiError(int status, String code, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, "Datos no validos: " + detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> onInvalid(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST, "El cuerpo de la peticion falta o no se puede leer");
    }

    @ExceptionHandler(SmtpNotConfiguredException.class)
    ResponseEntity<ApiError> onNotConfigured(SmtpNotConfiguredException e) {
        return error(HttpStatus.CONFLICT, "SMTP_NOT_CONFIGURED", e.getMessage());
    }

    /** El servidor de correo no acepto el envio: 502, con la causa para que el administrador la corrija. */
    @ExceptionHandler(SmtpSendException.class)
    ResponseEntity<ApiError> onSend(SmtpSendException e) {
        log.warn("Correo de prueba no enviado: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "SMTP_ERROR", e.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message));
    }
}
