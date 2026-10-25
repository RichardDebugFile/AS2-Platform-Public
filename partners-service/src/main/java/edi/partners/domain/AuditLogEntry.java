package edi.partners.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Evento de la bitacora. Inmutable: se crea completo y no tiene setters (la base de datos ademas
 * rechaza UPDATE y DELETE sobre la tabla).
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditStatus status;

    @Column(nullable = false, updatable = false, length = 64)
    private String actor;

    @Column(nullable = false, updatable = false, length = 32)
    private String source;

    @Column(name = "partner_id", updatable = false)
    private UUID partnerId;

    @Column(name = "partner_as2_name", updatable = false, length = 128)
    private String partnerAs2Name;

    @Column(name = "message_id", updatable = false)
    private String messageId;

    @Column(updatable = false, length = 2000)
    private String detail;

    @Column(updatable = false, columnDefinition = "text")
    private String changes;

    protected AuditLogEntry() {
    }

    @SuppressWarnings("java:S107") // un evento es exactamente estos datos; un builder no aporta claridad
    public AuditLogEntry(Instant occurredAt, String eventType, AuditStatus status, String actor, String source,
                         UUID partnerId, String partnerAs2Name, String messageId, String detail, String changes) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.status = status;
        this.actor = actor;
        this.source = source;
        this.partnerId = partnerId;
        this.partnerAs2Name = partnerAs2Name;
        this.messageId = messageId;
        this.detail = detail;
        this.changes = changes;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getActor() {
        return actor;
    }

    public String getSource() {
        return source;
    }

    public UUID getPartnerId() {
        return partnerId;
    }

    public String getPartnerAs2Name() {
        return partnerAs2Name;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getDetail() {
        return detail;
    }

    public String getChanges() {
        return changes;
    }
}
