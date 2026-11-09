package edi.as2adapter.as2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class As2ClientTest {

    private static final As2Identity PLATAFORMA = MessageBuilderTest.PLATAFORMA;
    private static final As2Identity SOCIO = MessageBuilderTest.SOCIO;

    private HttpServer server;
    private URI url;
    private final AtomicReference<String> reply = new AtomicReference<>("processed");
    private final AtomicReference<String> receivedPayload = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = SimulatedPartner.start(SOCIO, PLATAFORMA.certificate(), reply, receivedPayload);
        url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/as2");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SendResult send() {
        OutboundMessage msg = new MessageBuilder(Clock.systemUTC()).build(MessageBuilderTest.request("PLATAFORMA"));
        return new As2Client(HttpClient.newHttpClient(), Duration.ofSeconds(10)).send(url, msg, SOCIO.certificate());
    }

    @Test
    void envioAceptadoConMdnFirmadoYMicCoincidente() {
        SendResult r = send();

        assertThat(r.delivered()).isTrue();
        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(r.micMatches()).isTrue();
        assertThat(r.mdn().originalMessageId()).isEqualTo(r.messageId());
        assertThat(r.elapsed()).isPositive();
        assertThat(receivedPayload.get()).isEqualTo(MessageBuilderTest.X12);
    }

    @Test
    void unMdnConErrorOUnMicDistintoNoCuentaComoEntregado() {
        reply.set("error");
        assertThat(send().delivered()).isFalse();

        reply.set("bad-mic");
        SendResult r = send();
        assertThat(r.mdn().processed()).isTrue();
        assertThat(r.micMatches()).isFalse();
        assertThat(r.delivered()).isFalse();
    }

    @Test
    void unaRespuestaHttpDeErrorOSinConexionSeInforma() {
        reply.set("http-500");
        assertThatThrownBy(this::send).isInstanceOf(As2Exception.class).hasMessageContaining("HTTP 500");

        server.stop(0);
        assertThatThrownBy(this::send).isInstanceOf(As2Exception.class).hasMessageContaining("No se pudo conectar");
    }
}
