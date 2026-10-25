-- Configuraciones 1:1 del socio: salida, entrada y acuses MDN (HU-08). Se borran con el socio.
CREATE TABLE outgoing_config (
    id                    uuid PRIMARY KEY,
    partner_id            uuid NOT NULL UNIQUE REFERENCES partner (id) ON DELETE CASCADE,
    local_folder          varchar(512) NOT NULL,
    sending_mode          varchar(16)  NOT NULL,
    filename_filter       varchar(255),
    encryption_algorithm  varchar(16),
    signing_algorithm     varchar(16),
    compression           boolean      NOT NULL DEFAULT false,
    mime_type             varchar(32)  NOT NULL DEFAULT 'APPLICATION_EDI_X12',
    archive_mode          varchar(8)   NOT NULL DEFAULT 'MOVE',
    archive_folder        varchar(512),
    created_by            varchar(64),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_by            varchar(64),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_outgoing_sending_mode CHECK (sending_mode IN ('NONE', 'REGULAR', 'FILENAME_FILTER')),
    CONSTRAINT ck_outgoing_archive_mode CHECK (archive_mode IN ('NONE', 'MOVE', 'DELETE'))
);

CREATE TABLE incoming_config (
    id                uuid PRIMARY KEY,
    partner_id        uuid NOT NULL UNIQUE REFERENCES partner (id) ON DELETE CASCADE,
    incoming_folder   varchar(512) NOT NULL,
    parser_profile    varchar(128),
    created_by        varchar(64),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE mdn_config (
    id                uuid PRIMARY KEY,
    partner_id        uuid NOT NULL UNIQUE REFERENCES partner (id) ON DELETE CASCADE,
    local_folder      varchar(512) NOT NULL,
    receipt_type      varchar(8)   NOT NULL,
    delivery_mode     varchar(8)   NOT NULL,
    async_mdn_url     varchar(1024),
    created_by        varchar(64),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        varchar(64),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mdn_receipt_type  CHECK (receipt_type IN ('SIGNED', 'UNSIGNED')),
    CONSTRAINT ck_mdn_delivery_mode CHECK (delivery_mode IN ('SYNC', 'ASYNC'))
);
