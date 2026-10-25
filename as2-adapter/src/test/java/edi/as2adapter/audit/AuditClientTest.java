package edi.as2adapter.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class AuditClientTest {

    private static final AuditEvent SENT =
            new AuditEvent("DOCUMENT_SENT", "SUCCESS", "SOCIO-A", "<m1@as2.example.com>", "Factura enviada");

    @Test
    void publicaElEventoEnLaBitacoraDePartners() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://partners.test/internal/audit-logs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                    {"eventType": "DOCUMENT_SENT", "status": "SUCCESS", "source": "as2-adapter",
                     "partnerAs2Name": "SOCIO-A", "messageId": "<m1@as2.example.com>", "detail": "Factura enviada"}
                    """))
                .andRespond(withStatus(HttpStatus.CREATED));

        new AuditClient(builder, "http://partners.test", Runnable::run).publish(SENT);

        server.verify();
    }

    @Test
    void siLaBitacoraFalla_noLanza_yElEventoQuedaEnElLog(CapturedOutput out) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://partners.test/internal/audit-logs")).andRespond(withServerError());

        assertThatCode(() -> new AuditClient(builder, "http://partners.test", Runnable::run).publish(SENT))
                .doesNotThrowAnyException();

        assertThat(out.getOut()).contains("No se pudo registrar en la bitacora: event=DOCUMENT_SENT")
                .contains("messageId=<m1@as2.example.com>");
    }

    @Test
    void conPartnersCaido_vuelveDeInmediato_yRegistraElFalloEnSegundoPlano(CapturedOutput out) throws Exception {
        int puertoCerrado;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCerrado = s.getLocalPort();
        }
        java.util.List<Thread> hilos = new java.util.concurrent.CopyOnWriteArrayList<>();
        AuditClient client = new AuditClient(RestClient.builder(), "http://127.0.0.1:" + puertoCerrado,
                tarea -> hilos.add(Thread.ofVirtual().start(tarea)));

        long inicio = System.nanoTime();
        client.publish(SENT);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(500));

        for (Thread h : hilos) {
            assertThat(h.join(Duration.ofSeconds(10))).isTrue();
        }
        assertThat(out.getOut()).contains("No se pudo registrar en la bitacora: event=DOCUMENT_SENT");
    }

    @Test
    void elConstructorDeSpringUsaUnEjecutorEnSegundoPlano() {
        assertThatCode(() -> new AuditClient("http://127.0.0.1:1").publish(SENT)).doesNotThrowAnyException();
    }

    @Test
    void siElEjecutorRechazaLaTarea_tampocoLanza(CapturedOutput out) {
        AuditClient client = new AuditClient(RestClient.builder(), "http://partners.test", task -> {
            throw new java.util.concurrent.RejectedExecutionException("cerrado");
        });

        assertThatCode(() -> client.publish(SENT)).doesNotThrowAnyException();
        assertThat(out.getOut()).contains("RejectedExecutionException");
    }
}
