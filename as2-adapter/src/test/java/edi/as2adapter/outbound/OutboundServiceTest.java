package edi.as2adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.LocalIdentity;
import edi.as2adapter.as2.SimulatedPartner;
import edi.as2adapter.as2.TestPki;
import edi.as2adapter.audit.AuditClient;
import edi.as2adapter.audit.AuditEvent;
import edi.as2adapter.documents.As2Documents;
import edi.as2adapter.partner.PartnerCertificates;
import edi.as2adapter.partner.PartnerDirectory;
import edi.as2adapter.partner.PartnerTls;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboundServiceTest {

    private static final As2Identity LOCAL = TestPki.identity("plataforma.example");
    private static final As2Identity PEER = TestPki.identity("socio.example");
    private static final byte[] X12 = "ISA~\nGS~\nST~".getBytes(StandardCharsets.US_ASCII);
    private final UUID partnerId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final PartnerDirectory partners = mock(PartnerDirectory.class);
    private final As2Documents documents = mock(As2Documents.class);
    private final AuditClient audit = mock(AuditClient.class);
    private final LocalIdentity identity = mock(LocalIdentity.class);
    private final OutboundService service = new OutboundService(partners, new PartnerCertificates(3, true),
            new PartnerTls(300, false), identity, documents, audit);

    @BeforeEach
    void configure() throws Exception {
        when(identity.get()).thenReturn(LOCAL);
        when(identity.as2Id()).thenReturn("PLATAFORMA");
        when(identity.receiptAddress()).thenReturn("edi.plataforma@example.com");
        when(partners.certificates(partnerId, "ENCRYPTION")).thenReturn(List.of(certificate(PEER.certificate(), true)));
        when(partners.certificates(partnerId, "SIGNATURE")).thenReturn(List.of(certificate(PEER.certificate(), true)));
        when(documents.ingest(eq("SOCIO"), eq("OUTBOUND"), eq(X12), any(), eq("operador")))
                .thenReturn(new As2Documents.Ingested("CREATED",
                        new As2Documents.Document(documentId, "CREATED", "850", "SOCIO", "OUTBOUND")));
    }

    @Test
    void registraElMdnVerificadoYPublicaDocumentoEnviado() throws Exception {
        var received = new AtomicReference<String>();
        var partner = SimulatedPartner.start(PEER, LOCAL.certificate(), new AtomicReference<>("processed"), received);
        try {
            target("http://127.0.0.1:" + partner.getAddress().getPort() + "/as2", true);

            var result = service.send("SOCIO", X12, "orden.edi", "operador");

            assertThat(result.status()).isEqualTo("MDN_RECEIVED");
            assertThat(result.documentId()).isEqualTo(documentId);
            assertThat(result.mdnReceived()).isTrue();
            assertThat(received.get()).isEqualTo("ISA~\r\nGS~\r\nST~");
            var completion = ArgumentCaptor.forClass(As2Documents.Result.class);
            verify(documents).complete(eq(documentId), completion.capture());
            assertThat(completion.getValue().receipt().micMatches()).isTrue();
            assertThat(completion.getValue().receipt().signatureValid()).isTrue();
            assertThat(completion.getValue().receipt().rawMdn()).contains("multipart/signed", result.messageId());
            verify(audit).publish(new AuditEvent("DOCUMENT_SENT", "SUCCESS", "SOCIO", result.messageId(),
                    "Envío AS2 confirmado"));
        } finally {
            partner.stop(0);
        }
    }

    @Test
    void guardaComoFallidoElMdnConErrorYDevuelveElMotivo() throws Exception {
        var partner = SimulatedPartner.start(PEER, LOCAL.certificate(), new AtomicReference<>("decryption-failed"),
                new AtomicReference<>());
        try {
            target("http://127.0.0.1:" + partner.getAddress().getPort() + "/as2", true);

            var result = service.send("SOCIO", X12, "orden.edi", "operador");

            assertThat(result.status()).isEqualTo("SEND_FAILED");
            assertThat(result.reason()).contains("decryption-failed");
            var completion = ArgumentCaptor.forClass(As2Documents.Result.class);
            verify(documents).complete(eq(documentId), completion.capture());
            assertThat(completion.getValue().status()).isEqualTo("SEND_FAILED");
            assertThat(completion.getValue().receipt().disposition()).contains("decryption-failed");
            verify(audit).publish(new AuditEvent("DOCUMENT_SEND_FAILED", "FAILURE", "SOCIO", result.messageId(), result.reason()));
        } finally {
            partner.stop(0);
        }
    }

    @Test
    void tlsRechazaElServidorSinCertificadoYLoAdmiteAlCargarlo() throws Exception {
        As2Identity endpoint = TestPki.identity("localhost");
        var server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        try {
            server.setHttpsConfigurator(new HttpsConfigurator(serverContext(endpoint)));
            server.createContext("/as2", exchange -> {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            target("https://localhost:" + server.getAddress().getPort() + "/as2", false);

            var rejected = service.send("SOCIO", X12, "orden.edi", "operador");
            assertThat(rejected.status()).isEqualTo("SEND_FAILED");
            assertThat(rejected.reason()).contains("certificado SSL");

            when(partners.certificates(partnerId, "SSL")).thenReturn(List.of(certificate(endpoint.certificate(), true)));
            assertThat(service.send("SOCIO", X12, "orden.edi", "operador").status()).isEqualTo("DELIVERED");
        } finally {
            server.stop(0);
        }
    }

    private void target(String url, boolean mdn) {
        var partner = new PartnerDirectory.Partner(partnerId, "SOCIO", null, url, "RSA_SHA256", "AES256", "SHA256",
                2, 5, mdn, mdn, "SYNC", null, true, true);
        when(partners.find("SOCIO")).thenReturn(new PartnerDirectory.Agreement(partner, null, null));
    }

    @Test
    void rechazaUnAcuseAsincronoAntesDeRegistrarElEnvio() {
        var partner = new PartnerDirectory.Partner(partnerId, "SOCIO", null, "https://socio.example/as2",
                "RSA_SHA256", "AES256", "SHA256", 2, 5, true, true, "ASYNC", null, true, true);
        when(partners.find("SOCIO")).thenReturn(new PartnerDirectory.Agreement(partner, null, null));

        assertThatThrownBy(() -> service.send("SOCIO", X12, "orden.edi", "operador"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MDN síncrono");
        org.mockito.Mockito.verifyNoInteractions(documents);
    }

    private static PartnerDirectory.Certificate certificate(X509Certificate x509, boolean preferred) throws Exception {
        return new PartnerDirectory.Certificate(preferred, x509.getNotBefore().toInstant(), x509.getNotAfter().toInstant(),
                "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder().encodeToString(x509.getEncoded())
                        + "\n-----END CERTIFICATE-----");
    }

    private static SSLContext serverContext(As2Identity endpoint) throws Exception {
        char[] password = UUID.randomUUID().toString().toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("server", endpoint.key(), password, new X509Certificate[] {endpoint.certificate()});
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        var context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }
}
