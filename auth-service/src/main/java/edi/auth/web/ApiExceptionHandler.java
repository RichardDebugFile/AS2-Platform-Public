package edi.auth.web;

import edi.auth.dto.ApiError;
import edi.auth.error.AuthException;
import edi.auth.error.MfaAlreadyEnabledException;
import edi.auth.error.UserNotFoundException;
import edi.auth.error.UsernameTakenException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores con cuerpo {@code {status, code, message}}. Spring Boot no incluye el mensaje por defecto,
 * y sin el la consola solo veria un codigo HTTP mudo.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> onAuth(AuthException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UsernameTakenException.class)
    ResponseEntity<ApiError> onUsernameTaken(UsernameTakenException e) {
        return error(HttpStatus.CONFLICT, "USERNAME_TAKEN", e.getMessage());
    }

    @ExceptionHandler(MfaAlreadyEnabledException.class)
    ResponseEntity<ApiError> onMfaEnabled(MfaAlreadyEnabledException e) {
        return error(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> onNotFound(UserNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> onBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cuerpo de la petición no válido");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message));
    }
}
