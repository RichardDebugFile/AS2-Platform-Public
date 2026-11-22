package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundMessageTest {

    @Test
    void comparaLosBytesSinMostrarElContenidoDeLosDocumentos() {
        var a = new InboundMessage(Mime.ascii("ISA~"), Mime.ascii("MIC"), null);
        var b = new InboundMessage(Mime.ascii("ISA~"), Mime.ascii("MIC"), null);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo("ISA~");
        assertThat(a).isNotEqualTo(new InboundMessage(Mime.ascii("GS~"), b.micSource(), null))
                .isNotEqualTo(new InboundMessage(b.payload(), Mime.ascii("otro"), null))
                .isNotEqualTo(new InboundMessage(b.payload(), b.micSource(), Mime.ascii("firma")));
        assertThat(a.toString()).contains("payloadBytes=4", "signed=false").doesNotContain("ISA~", "MIC");
    }

    @Test
    void emiteUnMdnSinFirmaSiElAcuerdoLoPermite() {
        var report = new MdnWriter.Report("DESTINO", "<orden@example.com>", "processed", "QUJD", "sha-256");
        var body = MdnWriter.write(report, null);
        var parsed = MdnReader.read(body.contentType(), body.content(), List.of());
        assertThat(parsed.processed()).isTrue();
        assertThat(parsed.signed()).isFalse();
        assertThat(parsed.micMatches("QUJD", "sha-256")).isTrue();

        var copy = new MdnWriter.Body(body.contentType(), body.content().clone());
        assertThat(body).isEqualTo(copy).hasSameHashCodeAs(copy).isNotEqualTo(report)
                .isNotEqualTo(new MdnWriter.Body("text/plain", body.content()))
                .isNotEqualTo(new MdnWriter.Body(body.contentType(), new byte[0]));
        assertThat(body.toString()).contains("multipart/report", "bytes=").doesNotContain("<orden@example.com>");
    }

    @Test
    void rechazaTiposMimeNoAdmitidosYMixedSinDelimitador() {
        var key = MessageBuilderTest.PLATAFORMA;
        assertThatThrownBy(() -> InboundMessage.decode("text/html", null, new byte[0], key))
                .isInstanceOf(As2Exception.class).hasMessageContaining("Tipo MIME");
        assertThatThrownBy(() -> InboundMessage.decode("multipart/mixed", null, new byte[0], key))
                .isInstanceOf(As2Exception.class).hasMessageContaining("delimitador MIME");
    }

    @Test
    void extraeUnEdiAdjuntoSinModificarLaParteFirmada() {
        String boundary = Mime.boundary();
        byte[] payload = Mime.ascii("ISA~\r\nGS~");
        byte[] comment = Mime.part(Map.of("Content-Type", "text/plain"), Mime.ascii("Documento adjunto"));
        byte[] file = Mime.part(Map.of("Content-Type", "application/octet-stream",
                "Content-Disposition", "attachment; filename=\"orden.edi\"", "Content-Transfer-Encoding", "base64"),
                Mime.base64Lines(payload));
        byte[] mixed = Mime.part(Map.of("Content-Type", "multipart/mixed; boundary=" + boundary),
                Mime.multipart(boundary, List.of(comment, file)));
        var sender = MessageBuilderTest.PLATAFORMA;
        var recipient = MessageBuilderTest.SOCIO;
        byte[] signed = MessageBuilder.signedEntity(mixed, sender, "sha-256");
        byte[] cipher = Cms.encrypt(signed, recipient.certificate());

        var decoded = InboundMessage.decode("application/pkcs7-mime", "binary", cipher, recipient);

        assertThat(decoded.payload()).containsExactly(payload);
        assertThat(decoded.micSource()).containsExactly(mixed);
        assertThat(Cms.verify(decoded.micSource(), decoded.signature(), sender.certificate())).isTrue();
    }
}
