-- Socio comercial AS2: identidad, conexion, seguridad AS2, almacenamiento y contacto (HU-08).
CREATE TABLE partner (
    id                          uuid PRIMARY KEY,
    -- Identidad
    as2_name                    varchar(128) NOT NULL,
    partner_name                varchar(255),
    partner_as2_name            varchar(128),
    partner_type                varchar(32),
    external_id                 varchar(128),
    description                 text,
    -- Conexion con el servidor AS2 del socio
    server_address              varchar(255) NOT NULL,
    port                        integer      NOT NULL,
    transport_type              varchar(8)   NOT NULL,
    path                        varchar(255),
    basic_auth_username         varchar(255),
    basic_auth_password_enc     varchar(1024),
    -- NULL = se usan los valores globales del motor AS2
    connection_timeout_seconds  integer,
    read_timeout_seconds        integer,
    max_retry_attempts          integer,
    retry_backoff_ms            integer,
    -- Seguridad AS2
    signing_algorithm           varchar(16),
    encryption_algorithm        varchar(16),
    mic_algorithm               varchar(16),
    compression_enabled         boolean      NOT NULL DEFAULT false,
    request_mdn                 boolean      NOT NULL DEFAULT true,
    request_signed_mdn          boolean      NOT NULL DEFAULT true,
    mdn_mode                    varchar(8)   NOT NULL DEFAULT 'SYNC',
    mdn_receipt_url             varchar(1024),
    validate_certificate        boolean      NOT NULL DEFAULT true,
    validate_signature          boolean      NOT NULL DEFAULT true,
    validate_mic                boolean      NOT NULL DEFAULT true,
    -- Almacenamiento
    enable_dual_storage         boolean      NOT NULL DEFAULT true,
    enable_incoming_queue       boolean      NOT NULL DEFAULT false,
    shared_folder_name          varchar(255),
    data_location               varchar(1024),
    -- Contacto
    contact_name                varchar(255),
    contact_phone               varchar(50),
    contact_email               varchar(255),
    trading_partner_email       varchar(255),
    alert_on_failure            boolean      NOT NULL DEFAULT true,
    -- Estado (baja logica) y auditoria
    active                      boolean      NOT NULL DEFAULT true,
    created_by                  varchar(64),
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  varchar(64),
    updated_at                  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_partner_port           CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_partner_transport_type CHECK (transport_type IN ('HTTP', 'HTTPS')),
    CONSTRAINT ck_partner_mdn_mode       CHECK (mdn_mode IN ('SYNC', 'ASYNC')),
    CONSTRAINT ck_partner_timeouts       CHECK (
        (connection_timeout_seconds IS NULL OR connection_timeout_seconds > 0)
        AND (read_timeout_seconds IS NULL OR read_timeout_seconds > 0)
        AND (max_retry_attempts IS NULL OR max_retry_attempts >= 0)
        AND (retry_backoff_ms IS NULL OR retry_backoff_ms >= 0))
);

-- El identificador AS2 es unico entre los socios ACTIVOS: uno dado de baja no bloquea reutilizarlo.
CREATE UNIQUE INDEX uk_partner_as2_name_active ON partner (lower(as2_name)) WHERE active;
CREATE INDEX idx_partner_active ON partner (active);
