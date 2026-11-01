package edi.documents.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import edi.documents.store.Direction;
import org.junit.jupiter.api.Test;

/** El comando se compara por el contenido del documento y no lo escribe en los logs. */
class IngestCommandTest {

    private static IngestCommand command(String content) {
        return new IngestCommand("SOCIO-A", Direction.INBOUND, content.getBytes(), "<m1@example.com>", "ID-SOCIO",
                "ID-EMPRESA", null, null, null, null, null);
    }

    @Test
    void mismoContenido_sonIguales() {
        assertThat(command("ISA*00")).isEqualTo(command("ISA*00")).hasSameHashCodeAs(command("ISA*00"));
        assertThat(command("ISA*00")).isNotEqualTo(command("ISA*01")).isNotEqualTo(null).isNotEqualTo("ISA*00");
    }

    @Test
    void toString_noIncluyeElDocumento() {
        assertThat(command("ISA*00*SECRETO")).hasToString(
                "IngestCommand[partner=SOCIO-A, direction=INBOUND, messageId=<m1@example.com>, bytes=14]");
    }
}
