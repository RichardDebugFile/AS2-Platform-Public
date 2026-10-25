package edi.partners.svc.audit;

import edi.partners.domain.AuditLogEntry;
import edi.partners.domain.AuditStatus;
import edi.partners.repo.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda cada evento en la tabla {@code audit_log} dentro de la transaccion de quien lo publica: si la
 * operacion se deshace, su evento tambien (la bitacora no registra cambios que no ocurrieron). Deja
 * ademas una linea en el logger {@code audit}.
 */
@Component
public class JpaAuditLogPublisher implements AuditLogPublisher {

    private static final Logger log = LoggerFactory.getLogger("audit");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_DETAIL = 2000;

    private final AuditLogRepository repo;
    private final Clock clock;

    public JpaAuditLogPublisher(AuditLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuditLogEntry publish(AuditEvent e) {
        AuditLogEntry saved = repo.save(new AuditLogEntry(Instant.now(clock), e.eventType(),
                e.success() ? AuditStatus.SUCCESS : AuditStatus.FAILURE, e.actor(), e.source(), e.partnerId(),
                e.partnerAs2Name(), e.messageId(), truncate(e.detail()), json(e.changes())));
        log.info("event={} status={} partner={} actor={} source={} messageId={} detail={}", e.eventType(),
                saved.getStatus(), e.partnerAs2Name() != null ? e.partnerAs2Name() : e.partnerId(), e.actor(),
                e.source(), e.messageId(), e.detail());
        return saved;
    }

    static String json(Map<String, AuditEvent.Change> changes) {
        return changes.isEmpty() ? null : JSON.writeValueAsString(changes);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MAX_DETAIL ? s : s.substring(0, MAX_DETAIL - 1) + "…";
    }
}
