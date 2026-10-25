package edi.partners.web;

import edi.partners.dto.AuditLogRequest;
import edi.partners.dto.AuditLogResponse;
import edi.partners.svc.audit.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de eventos de otros servicios (motor AS2, documentos, alertas). El gateway no enruta
 * {@code /internal/**}: solo es alcanzable dentro de la red de contenedores. Quien publica no debe
 * esperar ni fallar por este registro (lo envia en segundo plano y, si falla, lo deja en su log).
 */
@RestController
@RequestMapping("/internal/audit-logs")
public class InternalAuditLogController {

    private final AuditLogService audit;

    public InternalAuditLogController(AuditLogService audit) {
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogResponse register(@Valid @RequestBody AuditLogRequest r) {
        return audit.register(r);
    }
}
