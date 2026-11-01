package edi.documents.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edi.documents.schema.X12ValidationService.Status;
import edi.documents.schema.X12ValidationService.ValidationResult;
import edi.documents.x12.Samples;
import edi.documents.x12.X12Document;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Validacion por esquema (AS2-72), con el esquema del 850 tal como lo carga la migracion. */
public class X12ValidationServiceTest {

    private X12ValidationService service;

    /** Para otras pruebas: fallos de cierre de los sobres de un documento. */
    public static List<String> envelope(X12Document d) {
        return X12ValidationService.envelopeProblems(d);
    }

    @BeforeEach
    void setUp() {
        DocumentSchemaRepository repo = mock(DocumentSchemaRepository.class);
        when(repo.findById("850")).thenReturn(Optional.of(new DocumentSchema("850", "Orden de compra",
                List.of("ISA", "GS", "ST", "BEG", "N1", "PO1", "CTT", "SE", "GE", "IEA"))));
        when(repo.findById("997")).thenReturn(Optional.empty());
        service = new X12ValidationService(repo);
    }

    @Test
    void un850Completo_quedaValidado() {
        ValidationResult r = service.validate(Samples.po850());

        assertThat(r.status()).isEqualTo(Status.VALIDATED);
        assertThat(r.typeCode()).isEqualTo("850");
        assertThat(r.version()).isEqualTo("4010");
        assertThat(r.missingSegments()).isEmpty();
        assertThat(r.problems()).isEmpty();
    }

    @Test
    void sinUnSegmentoObligatorio_fallaIndicandoCual() {
        String sinBeg = Samples.po850().replace("BEG*00*SA*OC-2026-001**20261101~\n", "").replace("SE*11*", "SE*10*");

        ValidationResult r = service.validate(sinBeg);

        assertThat(r.status()).isEqualTo(Status.VALIDATION_FAILED);
        assertThat(r.missingSegments()).containsExactly("BEG");
        assertThat(r.problems()).isEmpty();
    }

    @Test
    void sobresQueNoCierran_seInforman() {
        String roto = Samples.po850().replace("SE*11*0789", "SE*9*0790").replace("GE*1*456", "GE*2*455")
                .replace("IEA*1*000000123", "IEA*1*000000999");

        ValidationResult r = service.validate(roto);

        assertThat(r.status()).isEqualTo(Status.VALIDATION_FAILED);
        assertThat(r.problems()).hasSize(5).anyMatch(p -> p.startsWith("SE01"))
                .anyMatch(p -> p.startsWith("SE02")).anyMatch(p -> p.startsWith("GE01"))
                .anyMatch(p -> p.startsWith("GE02")).anyMatch(p -> p.startsWith("IEA02"));
    }

    @Test
    void tipoSinEsquema_oDocumentoIlegible_fallan() {
        assertThat(service.validate(Samples.ack997()).problems()).containsExactly("No hay esquema para el tipo 997");
        ValidationResult r = service.validate("hola");
        assertThat(r.status()).isEqualTo(Status.VALIDATION_FAILED);
        assertThat(r.typeCode()).isEqualTo(X12Document.UNKNOWN);
    }
}
