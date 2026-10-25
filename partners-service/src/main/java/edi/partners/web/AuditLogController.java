package edi.partners.web;

import edi.partners.domain.AuditStatus;
import edi.partners.dto.AuditLogQuery;
import edi.partners.dto.AuditLogResponse;
import edi.partners.dto.PageResponse;
import edi.partners.svc.audit.AuditCsv;
import edi.partners.svc.audit.AuditLogService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta y exportacion de la bitacora. Solo lectura: todos los roles (el gateway no deja escribir aqui). */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AuditLogService audit;
    private final Clock clock;

    public AuditLogController(AuditLogService audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) String partnerAs2Name,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var q = new AuditLogQuery(partnerId, partnerAs2Name, eventType, status, actor, messageId, from, to);
        return PageResponse.of(audit.search(q, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) String partnerAs2Name,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var q = new AuditLogQuery(partnerId, partnerAs2Name, eventType, status, actor, messageId, from, to);
        String file = "bitacora-" + LocalDate.now(clock.withZone(ZoneOffset.UTC)) + ".csv";
        String disposition = ContentDisposition.attachment().filename(file).build().toString();
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(AuditCsv.write(audit.export(q)));
    }

    @GetMapping("/event-types")
    public List<String> eventTypes() {
        return audit.eventTypes();
    }
}
