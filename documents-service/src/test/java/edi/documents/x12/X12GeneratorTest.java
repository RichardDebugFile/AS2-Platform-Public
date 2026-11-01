package edi.documents.x12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.documents.schema.X12ValidationServiceTest;
import edi.documents.x12.X12Document.Party;
import edi.documents.x12.X12Generator.Company;
import edi.documents.x12.X12Generator.Envelope;
import edi.documents.x12.X12Generator.Line;
import edi.documents.x12.X12Generator.Promotion;
import edi.documents.x12.X12Generator.PurchaseOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Escenarios de HU-12 sobre la generacion (AS2-70). */
class X12GeneratorTest {

    static final LocalDateTime AT = LocalDateTime.of(2026, 11, 1, 9, 30);
    static final Party EMPRESA = new Party("08", "ID-EMPRESA");
    static final Party SOCIO = new Party("12", "ID-SOCIO");

    static String po(String version) {
        return X12Generator.purchaseOrder(Envelope.of(EMPRESA, SOCIO, version, false, 42, AT), new PurchaseOrder(
                "OC-2026-002", LocalDate.of(2026, 11, 1), "00", "SA", "FLORES", LocalDate.of(2026, 11, 8),
                new Company("Comprador Ejemplo", "COMP-01"), new Company("Proveedor Ejemplo", "PROV-01"),
                List.of(new Line(new BigDecimal("120"), "EA", new BigDecimal("0.850"), "ROSA-ROJA-60",
                        "Rosa roja 60 cm"), new Line(new BigDecimal("40"), "EA", new BigDecimal("1.20"),
                                "TULIPAN-BLANCO", null))));
    }

    static String promo(String version) {
        return X12Generator.promotion(Envelope.of(EMPRESA, SOCIO, version, true, 77, AT), new Promotion("PROMO-NOV",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                new Company("Proveedor Ejemplo", "PROV-01")));
    }

    static String ack(String version) {
        String original = "4010".equals(version) ? Samples.po850() : po("5010");
        return X12Generator.functionalAck(X12Parser.parse(original), false, 99, AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4010", "5010"})
    void longitudesFijas_isaDe106YConteosCorrectos(String version) {
        for (String doc : List.of(po(version), promo(version), ack(version))) {
            String isa = doc.substring(0, doc.indexOf('\n'));
            assertThat(isa).hasSize(X12Parser.ISA_LENGTH);

            X12Document d = X12Parser.parse(doc);
            assertThat(d.complete()).isTrue();
            assertThat(d.version()).isEqualTo(version);
            assertThat(X12ValidationServiceTest.envelope(d)).isEmpty();
        }
    }

    @Test
    void acuse997_conIdentificadoresInvertidos() {
        X12Document original = X12Parser.parse(Samples.po850());

        X12Document ack = X12Parser.parse(X12Generator.functionalAck(original, false, 99, AT));

        assertThat(ack.typeCode()).isEqualTo("997");
        assertThat(ack.sender()).isEqualTo(new Party("08", "ID-EMPRESA"));
        assertThat(ack.receiver()).isEqualTo(new Party("12", "ID-SOCIO"));
        assertThat(ack.groupSender()).isEqualTo("ID-EMPRESA");
        assertThat(ack.groupReceiver()).isEqualTo("ID-SOCIO");
        assertThat(ack.functionalCode()).isEqualTo("FA");
        X12Segment ak1 = ack.first("AK1").orElseThrow();
        assertThat(ak1.element(1)).isEqualTo("PO");
        assertThat(ak1.element(2)).isEqualTo("456");
        X12Segment ak2 = ack.first("AK2").orElseThrow();
        assertThat(ak2.element(1)).isEqualTo("850");
        assertThat(ak2.element(2)).isEqualTo("0789");
        assertThat(ack.first("AK5").orElseThrow().element(1)).isEqualTo("A");
    }

    @Test
    void cadaDocumentoGenerado_seVuelveAInterpretarSinDiferencias() {
        X12Document d = X12Parser.parse(po("4010"));

        assertThat(d.typeCode()).isEqualTo("850");
        assertThat(d.functionalCode()).isEqualTo("PO");
        assertThat(d.sender()).isEqualTo(EMPRESA);
        assertThat(d.receiver()).isEqualTo(SOCIO);
        assertThat(d.interchangeControl()).isEqualTo("000000042");
        assertThat(d.groupControl()).isEqualTo("42");
        assertThat(d.transactionControl()).isEqualTo("0042");
        assertThat(d.interchangeDate()).isEqualTo("261101");
        assertThat(d.interchangeTime()).isEqualTo("0930");
        assertThat(d.usageIndicator()).isEqualTo("T");
        assertThat(d.first("PO1").orElseThrow().elements())
                .containsExactly("1", "120", "EA", "0.85", "", "VP", "ROSA-ROJA-60");
        assertThat(d.first("CTT").orElseThrow().element(1)).isEqualTo("2");

        X12Document p = X12Parser.parse(promo("4010"));
        assertThat(p.typeCode()).isEqualTo("889");
        assertThat(p.usageIndicator()).isEqualTo("P");
        assertThat(p.segments()).extracting(X12Segment::id)
                .containsExactly("ISA", "GS", "ST", "BSC", "DTM", "DTM", "N1", "SE", "GE", "IEA");
    }

    @Test
    void version5010_usaSuIsa12YGs08() {
        X12Document d = X12Parser.parse(po("5010"));

        assertThat(d.first("ISA").orElseThrow().element(12)).isEqualTo("00501");
        assertThat(d.first("ISA").orElseThrow().element(11)).isEqualTo("^");
        assertThat(d.first("GS").orElseThrow().element(8)).isEqualTo("005010");
    }

    @Test
    void datosQueRompenLaEstructura_seRechazan() {
        Envelope ok = Envelope.of(EMPRESA, SOCIO, "4010", false, 1, AT);
        Company c = new Company("Ejemplo", "E-1");
        List<Line> lines = List.of(new Line(BigDecimal.ONE, "EA", BigDecimal.ONE, "P-1", null));

        assertThatThrownBy(() -> X12Generator.purchaseOrder(ok, new PurchaseOrder("OC*1", LocalDate.of(2026, 1, 1),
                "00", "SA", null, LocalDate.of(2026, 1, 2), c, c, lines)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BEG03");
        assertThatThrownBy(() -> X12Generator.purchaseOrder(
                Envelope.of(new Party("08", "IDENTIFICADOR-MUY-LARGO"), SOCIO, "4010", false, 1, AT),
                new PurchaseOrder("OC-1", LocalDate.of(2026, 1, 1), "00", "SA", null, LocalDate.of(2026, 1, 2), c, c,
                        lines)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISA06");
        assertThatThrownBy(() -> X12Generator.purchaseOrder(Envelope.of(EMPRESA, SOCIO, "3060", false, 1, AT),
                new PurchaseOrder("OC-1", LocalDate.of(2026, 1, 1), "00", "SA", null, LocalDate.of(2026, 1, 2), c, c,
                        lines)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Version");
        assertThatThrownBy(() -> X12Generator.purchaseOrder(ok, new PurchaseOrder("OC-1", LocalDate.of(2026, 1, 1),
                "00", "SA", null, LocalDate.of(2026, 1, 2), c, c, List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linea");
        assertThatThrownBy(() -> X12Generator.purchaseOrder(Envelope.of(EMPRESA, SOCIO, "4010", false, 0, AT),
                new PurchaseOrder("OC-1", LocalDate.of(2026, 1, 1), "00", "SA", null, LocalDate.of(2026, 1, 2), c, c,
                        lines)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("control");
    }

    @Test
    void noSeAcusaUnDocumentoQueNoSeInterpreto() {
        assertThatThrownBy(() -> X12Generator.functionalAck(X12Parser.parse("texto cualquiera"), false, 1, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promocionQueTerminaAntesDeEmpezar_seRechaza() {
        assertThatThrownBy(() -> X12Generator.promotion(Envelope.of(EMPRESA, SOCIO, "4010", false, 1, AT),
                new Promotion("P-1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1),
                        new Company("Proveedor Ejemplo", "PROV-01"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
