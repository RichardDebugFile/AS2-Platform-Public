package edi.as2adapter.inbound;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import edi.as2adapter.As2AdapterApplication;
import edi.as2adapter.as2.As2Client;
import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.LocalIdentity;
import edi.as2adapter.as2.MdnReader;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.as2.OutboundMessage;
import edi.as2adapter.as2.SimulatedPartner;
import edi.as2adapter.as2.TestPki;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.audit.AuditEvent;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.partner.PartnerDirectory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"prod", "test"})
class InboundApiTest {

    private static final As2Identity LOCAL = TestPki.identity("destino.example");
    private static final As2Identity PEER = TestPki.identity("origen.example");
    private static final byte[] X12 = "ISA~GS~ST~".getBytes(US_ASCII);
    private final UUID partnerId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @LocalServerPort
    int port;
    @MockitoBean
    LocalIdentity identity;
    @MockitoBean
    PartnerDirectory partners;
    @MockitoBean
    As2Documents documents;
    @MockitoBean
    AuditClient audit;

    @BeforeEach
    void configure() throws Exception {
        when(identity.get()).thenReturn(LOCAL);
        when(identity.as2Id()).thenReturn("DESTINO");
        when(identity.receiptAddress()).thenReturn("edi.destino@example.com");
        var cert = new PartnerDirectory.Certificate(true, PEER.certificate().getNotBefore().toInstant(),
                PEER.certificate().getNotAfter().toInstant(), "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder().encodeToString(PEER.certificate().getEncoded()) + "\n-----END CERTIFICATE-----");
        when(partners.certificates(partnerId, "SIGNATURE")).thenReturn(List.of(cert));
        when(partners.certificates(partnerId, "ENCRYPTION")).thenReturn(List.of(cert));
        register(null, null);
    }

    @Test
    void recibeElEnvioHttpYDevuelveUnAcuseFirmadoConElMismoMic() {
        recordInbound("855", false);
        OutboundMessage message = build("ORIGEN", LOCAL);

        try (var transport = HttpClient.newHttpClient()) {
            var result = new As2Client(transport, Duration.ofSeconds(10))
                    .send(uri("/as2/receive"), message, LOCAL.certificate());
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.delivered()).isTrue();
            assertThat(result.rawMdn()).contains("multipart/signed", "multipart/report");
        }
        verify(documents).ingest(eq("ORIGEN"), eq("INBOUND"), eq(X12), any(), isNull());
    }

    @Test
    void dosInstanciasIntercambianElDocumentoYElMdnPorHttp(@TempDir Path keys) throws Exception {
        recordInbound("855", false);
        var dependencies = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            var written = new CompletableFuture<String>();
            UUID receiverId = UUID.randomUUID();
            var receiver = new PartnerDirectory.Partner(receiverId, "DESTINO", null, uri("/as2/receive").toString(),
                    "RSA_SHA256", "AES256", "SHA256", 3, 10, true, true, "SYNC", null, true, true);
            var cert = new PartnerDirectory.Certificate(true, LOCAL.certificate().getNotBefore().toInstant(),
                    LOCAL.certificate().getNotAfter().toInstant(), "-----BEGIN CERTIFICATE-----\n"
                    + Base64.getMimeEncoder().encodeToString(LOCAL.certificate().getEncoded()) + "\n-----END CERTIFICATE-----");
            dependencies.createContext("/internal/partners/search",
                    ex -> json(ex, new PartnerDirectory.Agreement(receiver, null, null)));
            dependencies.createContext("/internal/partners/" + receiverId + "/certificates", ex -> json(ex, List.of(cert)));
            dependencies.createContext("/api/documents/ingest/as2", ex -> json(ex,
                    new As2Documents.Ingested("CREATED",
                            new As2Documents.Document(documentId, "CREATED", "855", "DESTINO", "OUTBOUND"))));
            dependencies.createContext("/internal/as2/documents/" + documentId + "/result", ex -> {
                written.complete(new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                ex.sendResponseHeaders(204, -1);
                ex.close();
            });
            dependencies.createContext("/internal/audit-logs", ex -> {
                ex.sendResponseHeaders(201, -1);
                ex.close();
            });
            dependencies.start();
            String services = "http://127.0.0.1:" + dependencies.getAddress().getPort();
            String password = UUID.randomUUID().toString();
            Path key = TestPki.pkcs12(PEER, keys, "origen", password.toCharArray());
            try (var sender = (ServletWebServerApplicationContext) new SpringApplicationBuilder(As2AdapterApplication.class)
                    .run("--server.port=0", "--spring.main.banner-mode=off", "--AS2_OUR_AS2_ID=ORIGEN",
                            "--AS2_KEYSTORE_PATH=" + key, "--AS2_KEY_ALIAS=origen", "--AS2_KEYSTORE_PASSWORD=" + password,
                            "--PARTNERS_SERVICE_URL=" + services, "--DOCUMENTS_SERVICE_URL=" + services);
                    var http = HttpClient.newHttpClient()) {
                var target = URI.create("http://127.0.0.1:" + sender.getWebServer().getPort()
                        + "/api/as2/send?partner=DESTINO");
                var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/edi-x12")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(X12)).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(new ObjectMapper().readTree(response.body()).get("status").asString()).isEqualTo("MDN_RECEIVED");
            }
            assertThat(written.get(5, TimeUnit.SECONDS)).contains("MDN_RECEIVED", "multipart/signed");
            verify(documents).ingest(eq("ORIGEN"), eq("INBOUND"), eq(X12), any(), isNull());
        } finally {
            dependencies.stop(0);
        }
    }

    @Test
    void firmaElErrorDeDescifradoYNoIngiereElDocumento() throws Exception {
        var message = build("ORIGEN", TestPki.identity("otro.example"));
        var response = post("/as2/receive", message, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var mdn = MdnReader.read(response.headers().firstValue("Content-Type").orElseThrow(), response.body(), LOCAL.certificate());
        assertThat(mdn.signatureValid()).isTrue();
        assertThat(mdn.processed()).isFalse();
        assertThat(mdn.disposition()).endsWith("processed/Error: decryption-failed");
        verify(audit).publish(new AuditEvent("DECRYPTION_FAILED", "FAILURE", "ORIGEN", message.messageId(), "decryption-failed"));
        org.mockito.Mockito.verifyNoInteractions(documents);
    }

    @Test
    void unReintentoDevuelveProcessedYRegistraElDuplicado() throws Exception {
        recordInbound("855", true);
        var message = build("ORIGEN", LOCAL);
        for (int i = 0; i < 2; i++) {
            var response = post("/as2/receive", message, null);
            var mdn = MdnReader.read(response.headers().firstValue("Content-Type").orElseThrow(), response.body(), LOCAL.certificate());
            assertThat(mdn.processed()).isTrue();
            assertThat(mdn.micMatches(message.mic(), message.micAlgorithm())).isTrue();
        }
        verify(audit).publish(new AuditEvent("DUPLICATE_DETECTED", "SUCCESS", "ORIGEN", message.messageId(),
                "El documento ya estaba registrado"));
    }

    @Test
    void unSocioNoRegistradoRecibe400YLaRutaDePruebaEstaCerradaEnProd() throws Exception {
        var message = build("OTRO", LOCAL);
        var unknown = post("/as2/receive", message, null);
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(new String(unknown.body(), US_ASCII)).contains("Unknown AS2-From");
        assertThat(post("/as2/receive/test", message, null).statusCode()).isEqualTo(404);
    }

    @Test
    void elAcuerdoAsincronoEnviaElMdnPorPostTrasResponder200() throws Exception {
        recordInbound("855", false);
        var sent = new CompletableFuture<MdnReaderResult>();
        var callback = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            callback.createContext("/receipt", exchange -> {
                sent.complete(new MdnReaderResult(exchange.getRequestMethod(),
                        exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            callback.start();
            String destination = "http://127.0.0.1:" + callback.getAddress().getPort() + "/receipt";
            register(null, destination);
            var message = build("ORIGEN", LOCAL);

            var response = post("/as2/receive", message, destination);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEmpty();
            var received = sent.get(10, TimeUnit.SECONDS);
            assertThat(received.method()).isEqualTo("POST");
            var mdn = MdnReader.read(received.type(), received.body(), LOCAL.certificate());
            assertThat(mdn.signatureValid()).isTrue();
            assertThat(mdn.originalMessageId()).isEqualTo(message.messageId());
            assertThat(mdn.micMatches(message.mic(), message.micAlgorithm())).isTrue();
        } finally {
            callback.stop(0);
        }
    }

    @Test
    void rechazaUnaUrlDeAcuseDistintaDeLaConfiguradaParaElSocio() throws Exception {
        register(null, "http://127.0.0.1:19282/receipt");
        var response = post("/as2/receive", build("ORIGEN", LOCAL), "http://127.0.0.1:19283/receipt");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("no coincide con el acuerdo del socio");
        org.mockito.Mockito.verifyNoInteractions(documents);
    }

    @Test
    void un850EnviaEl997PorElMotorDeSalida() throws Exception {
        byte[] ack = "ISA~GS~ST*997~AK1*PO*456~AK2*850*0789~".getBytes(US_ASCII);
        var received = new AtomicReference<String>();
        var partner = SimulatedPartner.start(PEER, LOCAL.certificate(), new AtomicReference<>("processed"), received);
        try {
            register("http://127.0.0.1:" + partner.getAddress().getPort() + "/as2", null);
            recordInbound("850", false);
            when(documents.acknowledgment(X12)).thenReturn(ack);
            when(documents.ingest(eq("ORIGEN"), eq("OUTBOUND"), eq(ack), any(), isNull()))
                    .thenReturn(new As2Documents.Ingested("CREATED",
                            new As2Documents.Document(documentId, "CREATED", "997", "ORIGEN", "OUTBOUND")));
            assertThat(post("/as2/receive", build("ORIGEN", LOCAL), null).statusCode()).isEqualTo(200);
            verify(documents, timeout(8000)).complete(eq(documentId), any());
            assertThat(received.get()).isEqualTo(new String(ack, US_ASCII));
        } finally {
            partner.stop(0);
        }
    }

    private void register(String endpoint, String callback) {
        var partner = new PartnerDirectory.Partner(partnerId, "ORIGEN", null, endpoint, "RSA_SHA256", "AES256", "SHA256",
                2, 5, true, true, "SYNC", null, true, true);
        var receipt = callback == null ? null : new PartnerDirectory.Receipt("SIGNED", "ASYNC", callback);
        when(partners.find("ORIGEN")).thenReturn(new PartnerDirectory.Agreement(partner, null, receipt));
    }

    private void recordInbound(String type, boolean retry) {
        var document = new As2Documents.Document(documentId, "RECEIVED", type, "ORIGEN", "INBOUND");
        when(documents.ingest(eq("ORIGEN"), eq("INBOUND"), eq(X12), any(), isNull()))
                .thenReturn(new As2Documents.Ingested("CREATED", document),
                        new As2Documents.Ingested(retry ? "DUPLICATE" : "CREATED", document));
    }

    private static OutboundMessage build(String from, As2Identity recipient) {
        return new MessageBuilder(Clock.systemUTC()).build(new MessageBuilder.Request(from, "DESTINO",
                "edi.origen@example.com", X12, "application/edi-x12", "prueba.edi", PEER, recipient.certificate()));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<byte[]> post(String path, OutboundMessage message, String receipt) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(message.body()));
            message.httpHeaders().forEach(builder::header);
            if (receipt != null) builder.header("Receipt-Delivery-Option", receipt);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private record MdnReaderResult(String method, String type, byte[] body) {}

    private static void json(HttpExchange exchange, Object body) throws IOException {
        byte[] bytes = new ObjectMapper().writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
