package edi.documents.ingest;

import edi.documents.audit.AuditClient;
import edi.documents.audit.AuditEvent;
import edi.documents.config.RequestIdentity;
import edi.documents.store.Document;
import edi.documents.store.DocumentRepository;
import edi.documents.store.PartnerCopyStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Restaura las copias pendientes en la carpeta del socio: si la carpeta compartida del ERP estuvo caida
 * cuando llego un documento, esta tarea lo copia cuando vuelve. Corre cada hora sobre los ultimos 30 dias.
 */
@Component
public class PartnerCopyRecovery {

    static final Duration WINDOW = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(PartnerCopyRecovery.class);

    private final DocumentRepository documents;
    private final DocumentWriter writer;
    private final AuditClient audit;
    private final Clock clock;

    public PartnerCopyRecovery(DocumentRepository documents, DocumentWriter writer, AuditClient audit, Clock clock) {
        this.documents = documents;
        this.writer = writer;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "${documents.recovery.cron:0 0 * * * *}", zone = "${documents.zone:America/Guayaquil}")
    public void scheduled() {
        recover();
    }

    /** Devuelve cuantas copias restauro. */
    public int recover() {
        List<Document> pending = documents.findByPartnerCopyStatusAndCreatedAtAfterOrderByCreatedAtAsc(
                PartnerCopyStatus.PENDING, clock.instant().minus(WINDOW));
        int restored = 0;
        for (Document p : pending) {
            Document d = writer.copyToPartner(p.getId());
            if (d.getPartnerCopyStatus() == PartnerCopyStatus.COPIED) {
                restored++;
                log.info("Copia restaurada en la carpeta del socio: socio={} messageId={} archivo={}",
                        d.getPartnerAs2Name(), d.getMessageId(), d.getPartnerCopyPath());
                audit.publish(new AuditEvent("DOCUMENT_COPY_RESTORED", true, RequestIdentity.SYSTEM,
                        d.getPartnerAs2Name(), d.getMessageId(),
                        "Copia restaurada en la carpeta del socio tras su recuperacion"));
            }
        }
        if (!pending.isEmpty()) {
            log.info("Recuperacion de copias en las carpetas de los socios: {} de {} restauradas", restored,
                    pending.size());
        }
        return restored;
    }
}
