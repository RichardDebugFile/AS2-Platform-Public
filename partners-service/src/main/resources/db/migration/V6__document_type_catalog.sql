-- HU-13: catalogo de tipos de documento EDI, respuesta esperada de cada uno y tipos que intercambia
-- cada socio, en que direccion y con que datos adicionales.
CREATE TABLE document_type (
    id           uuid PRIMARY KEY,
    standard     varchar(10)  NOT NULL,
    code         varchar(10)  NOT NULL,
    version      varchar(10)  NOT NULL,
    name         varchar(120) NOT NULL,
    description  varchar(500),
    created_by   varchar(64),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   varchar(64),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_type_standard CHECK (standard IN ('X12', 'EDIFACT')),
    CONSTRAINT uq_document_type UNIQUE (standard, code, version)
);

-- Correlacion: el documento que se espera como respuesta (850 -> 855, 856, 810).
CREATE TABLE document_type_response (
    document_type_id uuid NOT NULL REFERENCES document_type (id) ON DELETE CASCADE,
    response_type_id uuid NOT NULL REFERENCES document_type (id) ON DELETE CASCADE,
    PRIMARY KEY (document_type_id, response_type_id),
    CONSTRAINT ck_document_type_response_self CHECK (document_type_id <> response_type_id)
);

-- Tipos por socio. Sin cascada hacia el catalogo: un tipo asignado no se puede borrar.
CREATE TABLE partner_document_type (
    id               uuid PRIMARY KEY,
    partner_id       uuid         NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    document_type_id uuid         NOT NULL REFERENCES document_type (id),
    direction        varchar(10)  NOT NULL,
    notes            varchar(500),
    extra_fields     text,
    CONSTRAINT ck_partner_document_type_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT uq_partner_document_type UNIQUE (partner_id, document_type_id, direction)
);

CREATE INDEX ix_partner_document_type_type ON partner_document_type (document_type_id);

-- Catalogo inicial: los documentos X12 4010 de la cadena de una orden de compra y su acuse.
INSERT INTO document_type (id, standard, code, version, name, created_by) VALUES
    (gen_random_uuid(), 'X12', '850', '4010', 'Orden de compra', 'system'),
    (gen_random_uuid(), 'X12', '855', '4010', 'Confirmacion de la orden de compra', 'system'),
    (gen_random_uuid(), 'X12', '856', '4010', 'Aviso de despacho', 'system'),
    (gen_random_uuid(), 'X12', '810', '4010', 'Factura', 'system'),
    (gen_random_uuid(), 'X12', '997', '4010', 'Acuse funcional', 'system'),
    (gen_random_uuid(), 'X12', '889', '4010', 'Anuncio de promocion', 'system');

INSERT INTO document_type_response (document_type_id, response_type_id)
SELECT o.id, r.id
FROM document_type o
JOIN document_type r ON r.standard = 'X12' AND r.version = '4010' AND r.code IN ('855', '856', '810')
WHERE o.standard = 'X12' AND o.version = '4010' AND o.code = '850';
