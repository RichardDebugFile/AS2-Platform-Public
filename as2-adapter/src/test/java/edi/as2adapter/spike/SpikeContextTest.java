package edi.as2adapter.spike;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edi.as2adapter.as2.As2Identity;
import edi.as2adapter.as2.TestPki;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La aplicacion arranca con el spike activado, como en deploy/interop: Spring debe poder crear el
 * controlador con su configuracion. El socio apunta a un puerto cerrado, asi que el envio responde 502.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpikeContextTest {

    @DynamicPropertySource
    static void spike(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("spike-context");
        dir.toFile().deleteOnExit();
        As2Identity plataforma = TestPki.identity("plataforma.example");
        registry.add("as2.spike.enabled", () -> "true");
        registry.add("as2.spike.url", () -> "http://127.0.0.1:1/as2");
        registry.add("as2.spike.from", () -> "PLATAFORMA");
        registry.add("as2.spike.to", () -> "OPENAS2");
        registry.add("as2.spike.mdn-to", () -> "edi.plataforma@example.com");
        Path keystore = TestPki.pkcs12(plataforma, dir, "plataforma", "clave-de-prueba".toCharArray());
        registry.add("as2.spike.keystore", keystore::toString);
        registry.add("as2.spike.keystore-password", () -> "clave-de-prueba");
        registry.add("as2.spike.key-alias", () -> "plataforma");
        Path partner = TestPki.der(TestPki.identity("openas2.example").certificate(), dir, "openas2");
        registry.add("as2.spike.partner-certificate", partner::toString);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void elControladorDelSpikeArrancaConSuConfiguracion() throws Exception {
        mvc.perform(post("/spike/send").content("ISA*00~"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("No se pudo conectar")));
    }
}
