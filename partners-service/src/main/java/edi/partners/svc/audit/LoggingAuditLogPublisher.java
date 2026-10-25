package edi.partners.svc.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implementacion provisional: una linea por evento en el logger {@code audit}. */
@Component
public class LoggingAuditLogPublisher implements AuditLogPublisher {

    private static final Logger log = LoggerFactory.getLogger("audit");

    @Override
    public void publish(AuditEvent e) {
        log.info("action={} partnerId={} actor={} success={} detail={}",
                e.action(), e.partnerId(), e.actor(), e.success(), e.detail());
    }
}
