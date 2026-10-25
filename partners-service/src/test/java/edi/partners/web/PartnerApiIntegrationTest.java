package edi.partners.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edi.partners.config.RequestIdentity;
import edi.partners.repo.PartnerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Criterios de aceptacion de HU-08 de punta a punta por HTTP (servicio completo sobre H2).
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "partners.storage.root=/data/partners"
})
@AutoConfigureMockMvc
class PartnerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PartnerRepository partners;

    @AfterEach
    void clean() {
        partners.deleteAll();
    }

    static String partnerJson(String as2Name) {
        return """
            {"as2Name": "%s", "partnerName": "Socio de prueba", "serverAddress": "as2.socio.example",
             "port": 4443, "transportType": "HTTPS", "path": "/as2", "signingAlgorithm": "sha-256",
             "encryptionAlgorithm": "AES256", "micAlgorithm": "SHA256",
             "basicAuthUsername": "usuario", "basicAuthPassword": "secreto"}
            """.formatted(as2Name);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(RequestIdentity.USER_HEADER, "operador1"));
    }

    private String createComplete(String as2Name) throws Exception {
        String body = postJson("/api/partners/complete", "{\"partner\": " + partnerJson(as2Name) + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.partner.id");
    }

    @Test
    void altaCompleta_dejaElSocioActivoConSusTresConfiguraciones() throws Exception {
        postJson("/api/partners/complete", "{\"partner\": " + partnerJson("SOCIO-A") + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partner.active").value(true))
                .andExpect(jsonPath("$.partner.signingAlgorithm").value("RSA_SHA256"))
                .andExpect(jsonPath("$.partner.endpointUrl").value("https://as2.socio.example:4443/as2"))
                .andExpect(jsonPath("$.partner.hasBasicAuthPassword").value(true))
                .andExpect(jsonPath("$.partner.basicAuthPassword").doesNotExist())
                .andExpect(jsonPath("$.partner.createdBy").value("operador1"))
                .andExpect(jsonPath("$.outgoing.localFolder", containsString("Outgoing")))
                .andExpect(jsonPath("$.incoming.incomingFolder", containsString("Incoming")))
                .andExpect(jsonPath("$.mdn.receiptType").value("SIGNED"));
    }

    @Test
    void identificadorDuplicado_409ConMensajeClaro() throws Exception {
        createComplete("SOCIO-A");

        postJson("/api/partners", partnerJson("socio-a"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AS2_NAME_TAKEN"))
                .andExpect(jsonPath("$.message", containsString("socio-a")));
    }

    @Test
    void reutilizarElIdentificadorDeUnSocioDadoDeBaja() throws Exception {
        String id = createComplete("SOCIO-B");
        mvc.perform(delete("/api/partners/" + id)).andExpect(status().isNoContent());

        createComplete("SOCIO-B");

        mvc.perform(get("/api/partners")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/partners/inactive")).andExpect(jsonPath("$[0].as2Name").value("SOCIO-B"));
        mvc.perform(patch("/api/partners/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nombreInvalidoParaCarpeta_400ConLaRegla() throws Exception {
        postJson("/api/partners", partnerJson("COM1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reservado")));
        postJson("/api/partners", partnerJson("Socio/Prueba"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("carpetas")));
        assertThat(partners.count()).isZero();
    }

    @Test
    void validacionesDelCuerpo_400() throws Exception {
        postJson("/api/partners", "{\"as2Name\": \"X\", \"serverAddress\": \"h.example\", \"port\": 70000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message", containsString("port")));
        postJson("/api/partners", partnerJson("SOCIO-C").replace("sha-256", "MD5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("MD5")));
        postJson("/api/partners", "{no es json")
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultaInternaPorElMotorAs2_devuelveIdYConfiguracion() throws Exception {
        String id = createComplete("SOCIO-A");

        mvc.perform(get("/internal/partners/search").param("as2Name", "socio-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner.id").value(id))
                .andExpect(jsonPath("$.outgoing.sendingMode").value("REGULAR"))
                .andExpect(jsonPath("$.mdn.deliveryMode").value("SYNC"));
        mvc.perform(get("/internal/partners/filewatcher-enabled"))
                .andExpect(jsonPath("$[0]").value("SOCIO-A"));
        mvc.perform(get("/internal/partners/search").param("as2Name", "NADIE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTNER_NOT_FOUND"));
    }

    @Test
    void edicionYConfiguraciones() throws Exception {
        String id = createComplete("SOCIO-D");

        mvc.perform(put("/api/partners/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(partnerJson("SOCIO-D").replace("4443", "443")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.port").value(443));
        mvc.perform(put("/api/partners/" + id + "/outgoing-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localFolder\": \"/out\", \"sendingMode\": \"FILENAME_FILTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("filtro")));
        mvc.perform(put("/api/partners/" + id + "/incoming-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incomingFolder\": \"/in\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/partners/" + id + "/mdn-config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localFolder\": \"/mdn\", \"receiptType\": \"UNSIGNED\", \"deliveryMode\": \"SYNC\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/partners/" + id + "/outgoing-config")).andExpect(status().isOk());
        mvc.perform(get("/api/partners/" + id + "/incoming-config")).andExpect(jsonPath("$.incomingFolder").value("/in"));
        mvc.perform(get("/api/partners/" + id + "/mdn-config")).andExpect(jsonPath("$.receiptType").value("UNSIGNED"));
        mvc.perform(get("/api/partners/" + id)).andExpect(jsonPath("$.as2Name").value("SOCIO-D"));
    }

    @Test
    void borradoDefinitivo_exigeConfirmacion() throws Exception {
        String id = createComplete("SOCIO-E");

        mvc.perform(delete("/api/partners/" + id + "/purge").param("confirm", "otro"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/partners/" + id + "/purge"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/partners/" + id + "/purge").param("confirm", "SOCIO-E"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/partners/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/partners/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void catalogoYSugerenciaDeAlgoritmos() throws Exception {
        String id = createComplete("SOCIO-F");

        mvc.perform(get("/api/catalogs/algorithms"))
                .andExpect(jsonPath("$.signing[0].key").value("RSA_SHA256"))
                .andExpect(jsonPath("$.encryption[3].status").value("legacy"));
        mvc.perform(get("/api/partners/" + id + "/algorithm-suggestion"))
                .andExpect(jsonPath("$.encryption").value("AES256"));
        mvc.perform(get("/api/partners/" + id + "/algorithm-suggestion").param("legacyCompatibility", "true"))
                .andExpect(jsonPath("$.signing").value("RSA_SHA1"));
    }
}
