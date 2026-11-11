package edi.as2adapter.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.SimulatedPartner;
import edi.as2adapter.as2.TestPki;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpikeControllerTest {

    private static final As2Identity PLATAFORMA = TestPki.identity("plataforma.example");
    private static final As2Identity SOCIO = TestPki.identity("socio.example");
    private static final char[] PASSWORD = "clave-de-prueba".toCharArray();

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicReference<String> reply = new AtomicReference<>("processed");
    private final AtomicReference<String> received = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = SimulatedPartner.start(SOCIO, PLATAFORMA.certificate(), reply, received);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SpikeProperties props(String alias) throws Exception {
        return new SpikeProperties(true, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/as2"),
                "PLATAFORMA", "SOCIO", "edi.plataforma@example.com", TestPki.pkcs12(PLATAFORMA, dir, "plataforma", PASSWORD),
                new String(PASSWORD), alias, TestPki.der(SOCIO.certificate(), dir, "socio"));
    }

    private MockMvc mvc(SpikeProperties p) {
        return MockMvcBuilders.standaloneSetup(new SpikeController(p)).build();
    }

    @Test
    void enviaElDocumentoYResumeElMdn() throws Exception {
        mvc(props(null)).perform(post("/spike/send").param("fileName", "orden-850.edi")
                        .contentType(MediaType.TEXT_PLAIN).content("ISA*00~ST*850*0001~SE*2*0001~"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(true))
                .andExpect(jsonPath("$.mdnSignatureValid").value(true))
                .andExpect(jsonPath("$.micMatches").value(true))
                .andExpect(jsonPath("$.micAlgorithm").value("sha-256"))
                .andExpect(jsonPath("$.disposition").value("automatic-action/MDN-sent-automatically; processed"));
        assertThat(received.get()).isEqualTo("ISA*00~ST*850*0001~SE*2*0001~");
    }

    @Test
    void unFalloDelSocioResponde502ConElMotivo() throws Exception {
        reply.set("http-500");
        mvc(props("plataforma")).perform(post("/spike/send").content("x".getBytes(StandardCharsets.US_ASCII)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El socio respondio HTTP 500 sin MDN"));
    }

    @Test
    void elNombreDelArchivoSeLimpiaAntesDeUsarloEnCabeceras() {
        assertThat(SpikeController.safeFileName("orden-850.edi")).isEqualTo("orden-850.edi");
        assertThat(SpikeController.safeFileName("a\r\nX-Falsa: 1.edi"))
                .isEqualTo("a__X-Falsa__1.edi");
        assertThat(SpikeController.safeFileName("..")).isEqualTo("documento.edi");
        assertThat(SpikeController.safeFileName(null)).isEqualTo("documento.edi");
        assertThat(SpikeController.safeFileName("x".repeat(300))).hasSize(100);
    }

    @Test
    void unAlmacenOCertificadoInvalidosSeRechazanAlArrancar() throws Exception {
        SpikeProperties ok = props(null);
        assertThatThrownBy(() -> new SpikeController(new SpikeProperties(true, ok.url(), "A", "B", "c", ok.keystore(),
                "otra-clave", null, ok.partnerCertificate()))).isInstanceOf(As2Exception.class);
        assertThatThrownBy(() -> new SpikeController(new SpikeProperties(true, ok.url(), "A", "B", "c", ok.keystore(),
                new String(PASSWORD), "no-existe", ok.partnerCertificate()))).isInstanceOf(As2Exception.class);
        assertThatThrownBy(() -> new SpikeController(new SpikeProperties(true, ok.url(), "A", "B", "c", ok.keystore(),
                new String(PASSWORD), null, dir.resolve("falta.cer")))).isInstanceOf(As2Exception.class);
    }
}
