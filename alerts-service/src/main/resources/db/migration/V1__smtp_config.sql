-- Configuracion del servidor de correo (AS2-106, adelanto de HU-21). Una sola fila (id = 1).
-- La contrasena se guarda cifrada con AES-256-GCM, con el prefijo "gcm:", y nunca se devuelve.
CREATE TABLE smtp_config (
    id           SMALLINT     PRIMARY KEY CHECK (id = 1),
    host         VARCHAR(255) NOT NULL,
    port         INTEGER      NOT NULL CHECK (port BETWEEN 1 AND 65535),
    security     VARCHAR(10)  NOT NULL CHECK (security IN ('NONE', 'STARTTLS', 'SSL')),
    username     VARCHAR(255),
    password_enc VARCHAR(512),
    from_address VARCHAR(255) NOT NULL,
    from_name    VARCHAR(120),
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   VARCHAR(64)  NOT NULL
);
