package edi.documents.schema;

import static org.assertj.core.api.Assertions.assertThat;

import edi.documents.testsupport.EnabledIfPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/** La migracion se aplica sobre PostgreSQL real y Hibernate valida la entidad contra la tabla. */
@EnabledIfPostgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentSchemaPostgresTest {

    @Autowired
    private DocumentSchemaRepository schemas;

    @Test
    void laMigracionCargaLosEsquemasDe850_997_y_889() {
        assertThat(schemas.findAll()).extracting(DocumentSchema::getTypeCode)
                .containsExactlyInAnyOrder("850", "997", "889");
        assertThat(schemas.findById("850").orElseThrow().getRequiredSegments())
                .containsExactly("ISA", "GS", "ST", "BEG", "N1", "PO1", "CTT", "SE", "GE", "IEA");
    }
}
