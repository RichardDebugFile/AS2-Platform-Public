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
    public static final String FOLDERS_REPAIRED = "FOLDERS_REPAIRED";
    public static final String FOLDERS_LINKED = "FOLDERS_LINKED";
    public static final String PRODUCTION_ENABLED = "PRODUCTION_ENABLED";
    public static final String PRODUCTION_DISABLED = "PRODUCTION_DISABLED";
    public static final String CERTIFICATE_UPLOADED = "CERTIFICATE_UPLOADED";
    public static final String CERTIFICATE_REACTIVATED = "CERTIFICATE_REACTIVATED";
    public static final String CERTIFICATE_PREFERRED = "CERTIFICATE_PREFERRED";
    public static final String CERTIFICATE_ACTIVATED = "CERTIFICATE_ACTIVATED";
    public static final String CERTIFICATE_DEACTIVATED = "CERTIFICATE_DEACTIVATED";
    public static final String CERTIFICATE_UPDATED = "CERTIFICATE_UPDATED";
    public static final String CERTIFICATE_DELETED = "CERTIFICATE_DELETED";
    /** Retiro diario: un certificado caducado se desactiva porque queda otro vigente del mismo uso. */
    public static final String CERTIFICATE_RETIRED = "CERTIFICATE_RETIRED";
    /** Retiro diario: el unico certificado activo de un uso caduco y se conserva (advertencia). */
    public static final String CERTIFICATE_EXPIRED = "CERTIFICATE_EXPIRED";

    private AuditEventTypes() {
    }
}
