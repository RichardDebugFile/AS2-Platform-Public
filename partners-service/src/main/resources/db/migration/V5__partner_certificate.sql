-- HU-11: certificados X.509 de los socios. Solo la parte publica: nunca se guarda una clave privada.
-- Doble compuerta: varios certificados activos por socio y uso (el nuevo y el saliente durante una
-- rotacion); como maximo uno preferente por socio y uso. Se borran con el socio.
CREATE TABLE partner_certificate (
    id                  uuid PRIMARY KEY,
    partner_id          uuid          NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    key_usage           varchar(16)   NOT NULL,
    issued_to           varchar(255)  NOT NULL,
    issued_by           varchar(255)  NOT NULL,
    subject_dn          varchar(1024) NOT NULL,
    issuer_dn           varchar(1024) NOT NULL,
    serial_number       varchar(128)  NOT NULL,
    fingerprint_sha256  varchar(64)   NOT NULL,
    valid_from          timestamptz   NOT NULL,
    valid_to            timestamptz   NOT NULL,
    cert_pem            text          NOT NULL,
    active              boolean       NOT NULL DEFAULT true,
    preferred           boolean       NOT NULL DEFAULT false,
    description         varchar(255),
    created_by          varchar(64),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          varchar(64),
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_certificate_key_usage CHECK (key_usage IN ('ENCRYPTION', 'SIGNATURE', 'SSL')),
    CONSTRAINT ck_certificate_validity CHECK (valid_to > valid_from),
    CONSTRAINT ck_certificate_fingerprint CHECK (fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_certificate_preferred_active CHECK (NOT preferred OR active),
    CONSTRAINT uq_certificate_partner_usage_fingerprint UNIQUE (partner_id, key_usage, fingerprint_sha256)
);

-- RN-12: como maximo un preferente por socio y uso.
CREATE UNIQUE INDEX uq_certificate_preferred ON partner_certificate (partner_id, key_usage) WHERE preferred;

-- Retiro diario de los caducados: solo recorre los activos.
CREATE INDEX ix_certificate_active_valid_to ON partner_certificate (valid_to) WHERE active;
