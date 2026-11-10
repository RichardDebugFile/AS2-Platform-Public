package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MessageBuilderTest {

    static final String X12 = "ISA*00*          *00*          *ZZ*PLATAFORMA     *ZZ*SOCIO          *261108*1200*U*00401*000000001*0*T*>~"
            + "GS*PO*PLATAFORMA*SOCIO*20261108*1200*1*X*004010~ST*850*0001~BEG*00*SA*OC-1**20261108~"
            + "SE*3*0001~GE*1*1~IEA*1*000000001~";

    static final As2Identity PLATAFORMA = TestPki.identity("plataforma.example");
    static final As2Identity SOCIO = TestPki.identity("socio.example");

    private final MessageBuilder builder =
            new MessageBuilder(Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC));

    static MessageBuilder.Request request(String from) {
        return new MessageBuilder.Request(from, "SOCIO", "edi.plataforma@example.com", X12.getBytes(StandardCharsets.US_ASCII),
                "application/edi-x12", "orden-850.edi", PLATAFORMA, SOCIO.certificate());
    }

    @Test
    void elSocioDescifraVerificaLaFirmaYObtieneElDocumentoIntacto() {
        OutboundMessage msg = builder.build(request("PLATAFORMA"));

        FakePartner.Received r = new FakePartner(SOCIO).receive(msg.body(), PLATAFORMA.certificate());

        assertThat(r.signatureValid()).isTrue();
        assertThat(new String(r.payload(), StandardCharsets.US_ASCII)).isEqualTo(X12);
        assertThat(r.payloadHeaders().get("content-type")).isEqualTo("application/edi-x12");
        assertThat(r.payloadHeaders().get("content-disposition")).contains("orden-850.edi");
        // El socio calcula el mismo MIC sobre la parte firmada, cabeceras incluidas.
        assertThat(r.mic()).isEqualTo(msg.mic());
        assertThat(msg.micAlgorithm()).isEqualTo("sha-256");
    }

    @Test
    void otraClaveNoPuedeDescifrarNiFirmarPorLaPlataforma() {
        OutboundMessage msg = builder.build(request("PLATAFORMA"));
        As2Identity otro = TestPki.identity("otro.example");

        assertThatThrownBy(() -> new FakePartner(otro).receive(msg.body(), PLATAFORMA.certificate()))
                .isInstanceOf(As2Exception.class);
        assertThat(new FakePartner(SOCIO).receive(msg.body(), otro.certificate()).signatureValid()).isFalse();
    }

    @Test
    void cabecerasAs2PidenUnMdnFirmadoSincrono() {
        OutboundMessage msg = builder.build(request("PLATAFORMA"));

        assertThat(msg.httpHeaders())
                .containsEntry("AS2-Version", "1.2")
                .containsEntry("AS2-From", "PLATAFORMA")
                .containsEntry("AS2-To", "SOCIO")
                .containsEntry("Disposition-Notification-To", "edi.plataforma@example.com")
                .containsEntry("Content-Type", "application/pkcs7-mime; smime-type=enveloped-data; name=\"smime.p7m\"");
        assertThat(msg.httpHeaders().get("Disposition-Notification-Options"))
                .isEqualTo("signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha-256");
        assertThat(msg.messageId()).startsWith("<20261108120000-").endsWith("@plataforma>");
        assertThat(builder.build(request("PLATAFORMA")).messageId()).isNotEqualTo(msg.messageId());
    }

    @Test
    void losRegistrosComparanElContenidoDeSusArreglosYNoLoMuestran() {
        MessageBuilder.Request a = request("PLATAFORMA");
        MessageBuilder.Request b = request("PLATAFORMA");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(request("OTRA")).isNotEqualTo("x");
        assertThat(a.toString()).contains("orden-850.edi").doesNotContain("ISA*");

        OutboundMessage m = builder.build(a);
        OutboundMessage copia = new OutboundMessage(m.messageId(), m.httpHeaders(), m.body().clone(), m.mic(),
                m.micAlgorithm());
        assertThat(m).isEqualTo(copia).hasSameHashCodeAs(copia).isNotEqualTo(builder.build(a));
        assertThat(m.toString()).contains(m.messageId()).contains(m.body().length + " bytes");
        assertThat(new OutboundMessage(null, null, null, null, null).toString()).contains("0 bytes");
        assertThat(new MessageBuilder.Request(null, null, null, null, null, null, null, null).toString())
                .contains("0 bytes");
    }

    @Test
    void identificadoresConEspaciosVanEntreComillas() {
        assertThat(MessageBuilder.quote("PLATAFORMA_01")).isEqualTo("PLATAFORMA_01");
        assertThat(MessageBuilder.quote("Mi Empresa")).isEqualTo("\"Mi Empresa\"");
        assertThat(builder.build(request("Mi Empresa")).messageId()).endsWith("@miempresa>");
        assertThat(builder.build(request("***")).messageId()).endsWith("@as2.local>");
    }
}
