package edi.partners.web;

import edi.partners.dto.ApiError;
import edi.partners.error.CertificateNotFoundException;
import edi.partners.error.ConfigNotFoundException;
import edi.partners.error.DuplicateAs2NameException;
import edi.partners.error.PartnerNotFoundException;
import edi.partners.error.FolderPrecheckException;
import edi.partners.error.PartnerStateException;
import edi.partners.svc.folders.PartnerFolderException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Errores con cuerpo {@code {status, code, message}} para que la consola explique el motivo. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PartnerNotFoundException.class)
    ResponseEntity<ApiError> onPartnerNotFound(PartnerNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ConfigNotFoundException.class)
    ResponseEntity<ApiError> onConfigNotFound(ConfigNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(CertificateNotFoundException.class)
    ResponseEntity<ApiError> onCertificateNotFound(CertificateNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "CERTIFICATE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> onTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "El archivo es demasiado grande para ser un certificado");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> onMissingPart(MissingServletRequestPartException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Falta el archivo del certificado ('" + e.getRequestPartName() + "')");
    }

    @ExceptionHandler(DuplicateAs2NameException.class)
    ResponseEntity<ApiError> onDuplicate(DuplicateAs2NameException e) {
        return error(HttpStatus.CONFLICT, "AS2_NAME_TAKEN", e.getMessage());
    }

    /** Dos altas simultaneas con el mismo identificador: la segunda choca con el indice unico. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onIntegrity(DataIntegrityViolationException e) {
        log.warn("Restriccion de la base incumplida: {}", e.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT", "Los datos chocan con otro registro (p. ej. identificador AS2 repetido)");
    }

    @ExceptionHandler(PartnerFolderException.class)
    ResponseEntity<ApiError> onFolders(PartnerFolderException e) {
        log.error("Operacion de carpetas del socio fallida", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "PARTNER_FOLDERS_FAILED", e.getMessage());
    }

    /** La verificacion previa encontro problemas: no se toco ninguna carpeta. */
    @ExceptionHandler(FolderPrecheckException.class)
    ResponseEntity<ApiError> onPrecheck(FolderPrecheckException e) {
        return error(HttpStatus.CONFLICT, "FOLDERS_PRECHECK_FAILED", e.getMessage());
    }

    @ExceptionHandler(PartnerStateException.class)
    ResponseEntity<ApiError> onState(PartnerStateException e) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", e.getMessage());
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> onUnreadable(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root instanceof IllegalArgumentException ? root.getMessage() : "Peticion no valida";
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), code, message));
    }
}
