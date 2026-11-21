package edi.as2adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.LocalIdentity;
import edi.as2adapter.as2.SimulatedPartner;
import edi.as2adapter.as2.TestPki;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.partner.PartnerCertificates;
import edi.as2adapter.partner.PartnerDirectory;
import edi.as2adapter.partner.PartnerTls;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class OutboundApiTest {

    private static final As2Identity LOCAL = TestPki.identity("origen.example");
    private static final As2Identity PEER = TestPki.identity("destino.example");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void completaElEnvioDesdeLaApiUsandoLosServiciosInternos(@TempDir Path keys) throws Exception {
        var partnerServer = SimulatedPartner.start(PEER, LOCAL.certificate(), new AtomicReference<>("processed"),
                new AtomicReference<>());
        var services = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            String target = "http://127.0.0.1:" + partnerServer.getAddress().getPort() + "/as2";
            UUID partnerId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            var peer = new PartnerDirectory.Partner(partnerId, "SOCIO", "DESTINO", target, "RSA_SHA256", "AES256",
                    "SHA256", 3, 10, true, true, "SYNC", null, true, true);
            var certificate = certificate(PEER.certificate());
            var responseCode = new AtomicInteger(200);
            var stored = new AtomicReference<As2Documents.Result>();
            services.createContext("/internal/partners/search", exchange -> {
                if (responseCode.get() != 200) {
                    respond(exchange, responseCode.get(), null);
                    return;
                }
                respond(exchange, 200, new PartnerDirectory.Agreement(peer, null, null));
            });
            services.createContext("/internal/partners/" + partnerId + "/certificates",
                    exchange -> respond(exchange, 200, List.of(certificate)));
            services.createContext("/api/documents/ingest/as2", exchange -> {
                exchange.getRequestBody().readAllBytes();
                respond(exchange, 201, new As2Documents.Ingested("CREATED", new As2Documents.Document(docId, "CREATED", "850")));
            });
            services.createContext("/internal/as2/documents/" + docId + "/result", exchange -> {
                stored.set(JSON.readValue(exchange.getRequestBody().readAllBytes(), As2Documents.Result.class));
                respond(exchange, 204, null);
            });
            services.createContext("/internal/audit-logs", exchange -> respond(exchange, 201, null));
            services.start();
            String base = "http://127.0.0.1:" + services.getAddress().getPort();
            String password = UUID.randomUUID().toString();
            Path key = TestPki.pkcs12(LOCAL, keys, "origen", password.toCharArray());
            var identity = new LocalIdentity("ORIGEN", "edi.origen@example.com", key.toString(), "origen", password);
            var service = new OutboundService(new PartnerDirectory(base), new PartnerCertificates(3, true),
                    new PartnerTls(300, false), identity, new As2Documents(base), new AuditClient(base));
            var mvc = MockMvcBuilders.standaloneSetup(new OutboundController(service)).build();

            var request = post("/api/as2/send").param("partner", "SOCIO").param("fileName", "ejemplo.edi")
                    .content("ISA~GS~ST~").header("X-User-Name", "operador");
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("MDN_RECEIVED"))
                    .andExpect(jsonPath("$.mdnReceived").value(true));
            assertThat(stored.get().receipt().signatureValid()).isTrue();
            assertThat(stored.get().receipt().micMatches()).isTrue();
            assertThat(identity.get()).isSameAs(identity.get());

            responseCode.set(404);
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Socio AS2 activo no encontrado"));
            responseCode.set(503);
            mvc.perform(request).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("No se pudo consultar la configuración del socio"));
        } finally {
            services.stop(0);
            partnerServer.stop(0);
        }
    }

    @Test
    void traduceErroresDelRepositorioYNoLosConfundeConUnEnvioCorrecto() throws Exception {
        var service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var code = new AtomicInteger(400);
            service.createContext("/api/documents/ingest/as2", ex -> respond(ex, code.get(), null));
            service.createContext("/internal/as2/documents", ex -> respond(ex, 503, null));
            service.start();
            var documents = new As2Documents("http://127.0.0.1:" + service.getAddress().getPort());
            assertThatThrownBy(() -> documents.ingest("SOCIO", "OUTBOUND", new byte[] {1}, Map.of(), null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rechazó");
            code.set(503);
            assertThatThrownBy(() -> documents.ingest("SOCIO", "OUTBOUND", new byte[] {1}, Map.of(), null))
                    .isInstanceOf(As2Exception.class).hasMessageContaining("No se pudo registrar");
            assertThatThrownBy(() -> documents.complete(UUID.randomUUID(), new As2Documents.Result("SEND_FAILED", "error", null)))
                    .isInstanceOf(As2Exception.class).hasMessageContaining("resultado del envío");
        } finally {
            service.stop(0);
        }
    }

    @Test
    void priorizaCertificadosVigentesYLimitaLaDobleCompuerta() throws Exception {
        var preferred = certificate(PEER.certificate());
        var original = certificate(LOCAL.certificate());
        var backup = new PartnerDirectory.Certificate(false, original.validFrom(), original.validTo(), original.pem());
        var now = Instant.now();
        var expired = new PartnerDirectory.Certificate(true, now.minusSeconds(600), now.minusSeconds(300), preferred.pem());
        var future = new PartnerDirectory.Certificate(false, now.plusSeconds(300), now.plusSeconds(600), preferred.pem());
        var withoutPem = new PartnerDirectory.Certificate(false, now.minusSeconds(300), now.plusSeconds(300), "");
        var resolver = new PartnerCertificates(2, true);

        assertThat(resolver.recipients(List.of(backup, preferred), now))
                .containsExactly(PEER.certificate(), LOCAL.certificate());
        assertThat(resolver.recipients(List.of(expired, backup, future, withoutPem), now))
                .containsExactly(LOCAL.certificate());
        assertThat(resolver.recipients(List.of(expired), now)).containsExactly(PEER.certificate());
        assertThat(resolver.recipients(List.of(future), now)).isEmpty();
        assertThat(new PartnerCertificates(2, false).recipients(List.of(backup, preferred), now))
                .containsExactly(PEER.certificate());
        assertThatThrownBy(() -> new PartnerCertificates(0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalIdentity("ORIGEN", "edi.origen@example.com", "", "", "").get())
                .isInstanceOf(As2Exception.class).hasMessageContaining("almacén de claves");
    }

    @Test
    void limitaElDestinoAs2AHttpSinCredencialesNiFragmentos() {
        assertThat(OutboundService.endpoint("https://socio.example/as2").getHost()).isEqualTo("socio.example");
        for (String rejected : List.of("", "file:///as2", "ftp://socio.example/as2",
                "https://usuario@example.com/as2", "https://socio.example/as2#recibo")) {
            assertThatThrownBy(() -> OutboundService.endpoint(rejected)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static PartnerDirectory.Certificate certificate(X509Certificate x509) throws Exception {
        return new PartnerDirectory.Certificate(true, x509.getNotBefore().toInstant(), x509.getNotAfter().toInstant(),
                "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder().encodeToString(x509.getEncoded())
                        + "\n-----END CERTIFICATE-----");
    }

    private static void respond(HttpExchange exchange, int code, Object body) throws IOException {
        try (exchange) {
            if (body == null) {
                exchange.sendResponseHeaders(code, -1);
                return;
            }
            byte[] bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
