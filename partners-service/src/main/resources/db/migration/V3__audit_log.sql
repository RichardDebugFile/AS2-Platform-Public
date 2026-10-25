-- HU-10: bitacora de auditoria. Un evento por cambio de configuracion o por hecho del intercambio
-- AS2 (envio, recepcion, MDN, certificados, fallos).
--
-- Sin clave foranea al socio: la bitacora conserva la historia aunque el socio se borre
-- definitivamente; por eso guarda tambien su identificador AS2 del momento.
CREATE TABLE audit_log (
    id               uuid          PRIMARY KEY,
    occurred_at      timestamptz   NOT NULL,
    event_type       varchar(64)   NOT NULL,
    status           varchar(16)   NOT NULL,
    actor            varchar(64)   NOT NULL,
    source           varchar(32)   NOT NULL,
    partner_id       uuid,
    partner_as2_name varchar(128),
    message_id       varchar(255),
    detail           varchar(2000),
    -- Datos cambiados en JSON: {"campo": {"from": ..., "to": ...}}. Nunca claves ni secretos.
    changes          text,
    CONSTRAINT ck_audit_log_status CHECK (status IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_audit_log_event_type CHECK (event_type ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_partner ON audit_log (partner_id, occurred_at DESC);
CREATE INDEX idx_audit_log_type ON audit_log (event_type, occurred_at DESC);
CREATE INDEX idx_audit_log_message ON audit_log (message_id) WHERE message_id IS NOT NULL;

-- Solo insercion: un evento registrado no se modifica ni se borra, ni siquiera desde el servicio.
CREATE FUNCTION audit_log_append_only() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log es de solo insercion: no se permite %', TG_OP;
END
$$;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
