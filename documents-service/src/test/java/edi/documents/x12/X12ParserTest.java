package edi.documents.x12;

import static org.assertj.core.api.Assertions.assertThat;

import edi.documents.x12.X12Document.Party;
import org.junit.jupiter.api.Test;

/** Escenarios de HU-12 sobre la interpretacion (AS2-69). */
class X12ParserTest {

    @Test
    void interpretacionDeUn850() {
        X12Document d = X12Parser.parse(Samples.po850());

        assertThat(d.typeCode()).isEqualTo("850");
        assertThat(d.version()).isEqualTo("4010");
        assertThat(d.functionalCode()).isEqualTo("PO");
        assertThat(d.interchangeControl()).isEqualTo("000000123");
        assertThat(d.groupControl()).isEqualTo("456");
        assertThat(d.transactionControl()).isEqualTo("0789");
        assertThat(d.sender()).isEqualTo(new Party("12", "ID-SOCIO"));
        assertThat(d.receiver()).isEqualTo(new Party("08", "ID-EMPRESA"));
        assertThat(d.groupSender()).isEqualTo("ID-SOCIO");
        assertThat(d.groupReceiver()).isEqualTo("ID-EMPRESA");
        assertThat(d.interchangeDate()).isEqualTo("261101");
        assertThat(d.interchangeTime()).isEqualTo("0930");
        assertThat(d.usageIndicator()).isEqualTo("T");
        assertThat(d.complete()).isTrue();
        assertThat(d.segments()).hasSize(15);
        assertThat(d.delimiters()).isEqualTo(new X12Delimiters('*', '>', "~"));
        assertThat(d.controls()).containsExactly(
                org.assertj.core.api.Assertions.entry("ISA13", "000000123"),
                org.assertj.core.api.Assertions.entry("GS06", "456"),
                org.assertj.core.api.Assertions.entry("ST02", "0789"));
    }

    @Test
    void finDeSegmentoPorSaltoDeLinea_elResultadoEsIdentico() {
        X12Document tilde = X12Parser.parse(Samples.po850TildeOnly());

        for (String variant : new String[] {Samples.po850(), Samples.po850Crlf(), Samples.po850Lf()}) {
            assertThat(X12Parser.parse(variant)).usingRecursiveComparison().ignoringFields("delimiters")
                    .isEqualTo(tilde);
        }
        assertThat(X12Parser.parse(Samples.po850Crlf()).delimiters().segment()).isEqualTo("\r\n");
        assertThat(X12Parser.parse(Samples.po850Lf()).delimiters().segment()).isEqualTo("\n");
    }

    @Test
    void losElementosConservanSuPosicionYLosVacios() {
        X12Document d = X12Parser.parse(Samples.po850());

        X12Segment beg = d.first("BEG").orElseThrow();
        assertThat(beg.element(3)).isEqualTo("OC-2026-001");
        assertThat(beg.element(4)).isEmpty();
        assertThat(beg.element(5)).isEqualTo("20261101");
        assertThat(beg.element(9)).isEmpty();
        assertThat(d.first("PID").orElseThrow().element(5)).isEqualTo("Rosa roja 60 cm");
    }

    @Test
    void acuse997Version5010_conOtroSeparadorDeComponentes() {
        X12Document d = X12Parser.parse(Samples.ack997());

        assertThat(d.typeCode()).isEqualTo("997");
        assertThat(d.version()).isEqualTo("5010");
        assertThat(d.delimiters().component()).isEqualTo(':');
        assertThat(d.sender()).isEqualTo(new Party("08", "ID-EMPRESA"));
        assertThat(d.first("AK2").orElseThrow().element(2)).isEqualTo("0789");
    }

    @Test
    void anuncio889_enProduccion_seValidaComoCompleto() {
        X12Document d = X12Parser.parse(Samples.promo889());

        assertThat(d.typeCode()).isEqualTo("889");
        assertThat(d.usageIndicator()).isEqualTo("P");
        assertThat(d.complete()).isTrue();
        assertThat(edi.documents.schema.X12ValidationServiceTest.envelope(d)).isEmpty();
    }

    @Test
    void documentoIncompleto_esUnknownSinError() {
        X12Document d = X12Parser.parse("ST*850*0001~BEG*00*SA*OC-1**20261101~");

        assertThat(d.typeCode()).isEqualTo(X12Document.UNKNOWN);
        assertThat(d.version()).isEqualTo(X12Document.UNKNOWN);
        assertThat(d.isKnown()).isFalse();
        assertThat(d.complete()).isFalse();
        assertThat(d.segments()).extracting(X12Segment::id).containsExactly("ST", "BEG");
    }

    @Test
    void textoVacioONuloOIsaCortado_sonUnknown() {
        assertThat(X12Parser.parse(null).typeCode()).isEqualTo(X12Document.UNKNOWN);
        assertThat(X12Parser.parse("   ").segments()).isEmpty();
        assertThat(X12Parser.parse(Samples.po850().substring(0, 60)).typeCode()).isEqualTo(X12Document.UNKNOWN);
    }

    @Test
    void isaConUnSeparadorDeMenos_esUnknown() {
        String roto = Samples.po850().replaceFirst("\\*00\\*", "*00#");

        assertThat(X12Parser.parse(roto).typeCode()).isEqualTo(X12Document.UNKNOWN);
    }

    @Test
    void marcaDeOrdenDeBytesYEspaciosAlInicio_seIgnoran() {
        X12Document d = X12Parser.parse("\uFEFF\n  " + Samples.po850());

        assertThat(d.typeCode()).isEqualTo("850");
        assertThat(d.complete()).isTrue();
    }

    @Test
    void version_sinGs_saleDelIsa() {
        assertThat(X12Parser.version("", "00401")).isEqualTo("4010");
        assertThat(X12Parser.version("005010X222A1", "00401")).isEqualTo("5010");
        assertThat(X12Parser.version("", "")).isEqualTo(X12Document.UNKNOWN);
    }
}
