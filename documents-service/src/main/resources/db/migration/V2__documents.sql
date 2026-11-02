-- HU-14: repositorio de documentos con almacenamiento dual. La base guarda los metadatos, el contenido
-- y el estado; el disco (/opt/as2_data/{inbound|outbound}/...) guarda una copia legible del archivo, y la
-- carpeta del socio recibe otra para el ERP. Los tipos son los de PostgreSQL y los admite H2 en las pruebas.
CREATE TABLE documents (
    id                   UUID          PRIMARY KEY,
    partner_as2_name     VARCHAR(128)  NOT NULL,
    direction            VARCHAR(10)   NOT NULL,
    type_code            VARCHAR(10)   NOT NULL,
    version              VARCHAR(10)   NOT NULL,
    as2_from             VARCHAR(128),
    as2_to               VARCHAR(128),
    -- Idempotencia: un reintento del socio trae el mismo Message-ID y no crea otro documento.
    message_id           VARCHAR(255)  NOT NULL,
    isa_control          VARCHAR(20),
    gs_control           VARCHAR(20),
    st_control           VARCHAR(20),
    mic                  VARCHAR(255),
    mic_algorithm        VARCHAR(20),
    signing_algorithm    VARCHAR(20),
    encryption_algorithm VARCHAR(20),
    size_bytes           BIGINT        NOT NULL,
    sha256               VARCHAR(64)   NOT NULL,
    status               VARCHAR(30)   NOT NULL,
    validation_detail    VARCHAR(2000),
    attributes           VARCHAR(4000),
    file_path            VARCHAR(1024) NOT NULL,
    partner_copy_path    VARCHAR(1024),
    partner_copy_status  VARCHAR(20)   NOT NULL,
    created_by           VARCHAR(128),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_documents_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_documents_copy CHECK (partner_copy_status IN ('COPIED', 'PENDING'))
);

CREATE UNIQUE INDEX uk_documents_message_id ON documents (message_id);
CREATE INDEX ix_documents_partner_date ON documents (partner_as2_name, created_at);
CREATE INDEX ix_documents_type_date ON documents (type_code, created_at);
CREATE INDEX ix_documents_isa ON documents (isa_control);
CREATE INDEX ix_documents_copy_pending ON documents (partner_copy_status, created_at);

-- Contenido tal como llego (1:1). Permite servirlo aunque falte el archivo y restaurar la copia del socio.
CREATE TABLE document_payloads (
    document_id UUID  PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    content     BYTEA NOT NULL
);

-- Acuse MDN del intercambio (1:1). Lo completan el envio y la recepcion AS2 (HU-15 y HU-16).
CREATE TABLE document_mdn (
    document_id     UUID         PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    disposition     VARCHAR(255) NOT NULL,
    received_mic    VARCHAR(255),
    mic_matches     BOOLEAN,
    signature_valid BOOLEAN,
    raw_mdn         VARCHAR(20000),
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
