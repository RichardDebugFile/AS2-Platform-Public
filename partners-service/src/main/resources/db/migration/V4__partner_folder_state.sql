-- HU-09: etapa de las carpetas del socio.
--   LOCAL         carpetas propias bajo la raiz de almacenamiento (estado inicial)
--   SAMBA_LINKED  cada subcarpeta es un enlace a la carpeta compartida del ERP
--   PRODUCTION    enlazado y con la cola de entrada (Incoming_queue) activa para el ERP
ALTER TABLE partner
    ADD COLUMN folder_state varchar(16) NOT NULL DEFAULT 'LOCAL',
    ADD CONSTRAINT ck_partner_folder_state CHECK (folder_state IN ('LOCAL', 'SAMBA_LINKED', 'PRODUCTION'));
