package edi.documents.ingest;

import edi.documents.audit.AuditClient;
import edi.documents.audit.AuditEvent;
import edi.documents.config.RequestIdentity;
import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentStatus;
import edi.documents.store.PartnerCopyStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ingesta de documentos (HU-14): guarda el documento (base, contenido y archivo, en una transaccion) y,
 * ya confirmado, lo copia a la carpeta del socio. Asi el ERP nunca recibe un documento que no quedo
 * registrado. Si la copia falla, la ingesta igual termina bien y la copia queda pendiente.
 */
@Service
public class DocumentIngestService {

    private final DocumentWriter writer;
    private final AuditClient audit;

    /** El documento guardado y si era nuevo o un reintento del socio. */
    public record IngestResult(Document document, IngestOutcome outcome) {
    }

    public DocumentIngestService(DocumentWriter writer, AuditClient audit) {
        this.writer = writer;
        this.audit = audit;
    }

    public IngestResult ingest(IngestCommand command) {
        String user = RequestIdentity.currentUser();
        DocumentWriter.Stored stored = writer.create(command, user);
        Document d = stored.document();
        if (stored.outcome() == IngestOutcome.DUPLICATE) {
            audit.publish(new AuditEvent("DOCUMENT_DUPLICATE", true, user, d.getPartnerAs2Name(), d.getMessageId(),
                    "Reintento con un Message-ID ya registrado: se devuelve el documento existente " + d.getTypeCode()));
            return new IngestResult(d, IngestOutcome.DUPLICATE);
        }
        d = writer.copyToPartner(d.getId());
        boolean valid = d.getStatus() != DocumentStatus.VALIDATION_FAILED;
        String event = d.getDirection() == Direction.INBOUND ? "DOCUMENT_RECEIVED" : "DOCUMENT_CREATED";
        String detail = "Documento " + d.getTypeCode() + " " + d.getVersion() + " guardado (ISA " + d.getIsaControl()
                + ")" + (valid ? "" : ": no cumple el esquema. " + d.getValidationDetail());
        audit.publish(new AuditEvent(event, valid, user, d.getPartnerAs2Name(), d.getMessageId(), detail));
        if (d.getPartnerCopyStatus() == PartnerCopyStatus.PENDING) {
            audit.publish(new AuditEvent("DOCUMENT_COPY_PENDING", false, user, d.getPartnerAs2Name(),
                    d.getMessageId(), "La carpeta del socio no esta disponible: la copia se reintentara cada hora"));
        }
        return new IngestResult(d, IngestOutcome.CREATED);
    }

    public Document delete(UUID id) {
        Document d = writer.delete(id);
        audit.publish(new AuditEvent("DOCUMENT_DELETED", true, RequestIdentity.currentUser(), d.getPartnerAs2Name(),
                d.getMessageId(), "Documento " + d.getTypeCode() + " borrado del repositorio"));
        return d;
    }
}
