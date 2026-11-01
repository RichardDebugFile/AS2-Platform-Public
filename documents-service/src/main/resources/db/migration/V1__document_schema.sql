-- HU-12: esquema minimo de cada tipo de documento X12, para validar lo que llega antes de procesarlo.
-- required_segments: identificadores de segmento obligatorios, separados por coma, en el orden del
-- documento. Se valida por tipo (ST01); la version no cambia los segmentos obligatorios de estos tipos.
CREATE TABLE document_schema (
    type_code         VARCHAR(6)   PRIMARY KEY,
    description       VARCHAR(120) NOT NULL,
    required_segments VARCHAR(500) NOT NULL
);

INSERT INTO document_schema (type_code, description, required_segments) VALUES
    ('850', 'Orden de compra', 'ISA,GS,ST,BEG,N1,PO1,CTT,SE,GE,IEA'),
    ('997', 'Acuse funcional', 'ISA,GS,ST,AK1,AK9,SE,GE,IEA'),
    ('889', 'Anuncio de promoción', 'ISA,GS,ST,BSC,DTM,N1,SE,GE,IEA');
