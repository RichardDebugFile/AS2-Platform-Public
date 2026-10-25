package edi.partners.svc.audit;

/** Tipos de evento que registra este servicio. Los del intercambio AS2 los define quien los publica. */
public final class AuditEventTypes {

    public static final String PARTNER_CREATED = "PARTNER_CREATED";
    public static final String PARTNER_UPDATED = "PARTNER_UPDATED";
    public static final String PARTNER_ACTIVATED = "PARTNER_ACTIVATED";
    public static final String PARTNER_DEACTIVATED = "PARTNER_DEACTIVATED";
    public static final String PARTNER_PURGED = "PARTNER_PURGED";
    public static final String OUTGOING_CONFIG_UPDATED = "OUTGOING_CONFIG_UPDATED";
    public static final String INCOMING_CONFIG_UPDATED = "INCOMING_CONFIG_UPDATED";
    public static final String MDN_CONFIG_UPDATED = "MDN_CONFIG_UPDATED";

    private AuditEventTypes() {
    }
}
