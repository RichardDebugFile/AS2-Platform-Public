package edi.documents.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.documents.testsupport.EnabledIfPostgres;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Esquema real del repositorio: la migracion V2 en PostgreSQL, con Hibernate validando las entidades. */
@EnabledIfPostgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentPostgresTest {

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentPayloadRepository payloads;

    @Autowired
    private JdbcTemplate jdbc;

    private static Document document(String messageId) {
        Document d = new Document(UUID.randomUUID(), "SOCIO-PG", Direction.INBOUND, messageId, Instant.now());
        d.describe("850", "4010", "000000001", "1", "0001");
        d.content(10, "0".repeat(64), "{\"canal\":\"prueba\"}", "prueba");
        d.status(DocumentStatus.RECEIVED, null);
        d.storedAt("/tmp/x.edi");
        return d;
    }

    @Test
    void elMessageIdEsUnico() {
        documents.saveAndFlush(document("<PG-1@example.com>"));

        assertThatThrownBy(() -> documents.saveAndFlush(document("<PG-1@example.com>")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarElDocumento_borraSuContenido() {
        Document d = documents.saveAndFlush(document("<PG-2@example.com>"));
        payloads.saveAndFlush(new DocumentPayload(d.getId(), "ISA*00".getBytes()));

        documents.delete(d);
        documents.flush();

        assertThat(jdbc.queryForObject("select count(*) from document_payloads where document_id = ?", Long.class,
                d.getId())).isZero();
    }
}
