package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundMessageTest {

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
