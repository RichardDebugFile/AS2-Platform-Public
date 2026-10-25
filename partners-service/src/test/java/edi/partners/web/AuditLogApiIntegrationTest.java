package edi.partners.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edi.partners.config.RequestIdentity;
import edi.partners.testsupport.TickingClock;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Criterios de aceptacion de HU-10 de punta a punta por HTTP (servicio completo sobre H2). */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:auditapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "partners.storage.root=/data/partners"
})
@AutoConfigureMockMvc
class AuditLogApiIntegrationTest {

    /** Cada evento con su propio instante, para que el orden cronologico sea determinista. */
    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock tickingClock() {
            return new TickingClock();
        }
    }

    @Autowired
    private MockMvc mvc;

    private String create(String as2Name) throws Exception {
        String body = mvc.perform(post("/api/partners/complete").contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdentity.USER_HEADER, "operador1")
                        .content("{\"partner\": " + PartnerApiIntegrationTest.partnerJson(as2Name) + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.partner.id");
    }

    private void internal(String json) throws Exception {
        mvc.perform(post("/internal/audit-logs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void cambioDelEndpoint_registraElUsuarioDeLaSesionYLosDatosCambiados() throws Exception {
        String id = create("SOCIO-AUD-A");
        String edit = PartnerApiIntegrationTest.partnerJson("SOCIO-AUD-A")
                .replace("as2.socio.example", "as2-nuevo.socio.example");
        mvc.perform(put("/api/partners/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdentity.USER_HEADER, "operador2").content(edit))
                .andExpect(status().isOk());

        mvc.perform(get("/api/audit-logs").param("partnerId", id).param("eventType", "PARTNER_UPDATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actor").value("operador2"))
                .andExpect(jsonPath("$.content[0].partnerAs2Name").value("SOCIO-AUD-A"))
                .andExpect(jsonPath("$.content[0].changes.serverAddress.from").value("as2.socio.example"))
                .andExpect(jsonPath("$.content[0].changes.serverAddress.to").value("as2-nuevo.socio.example"))
                .andExpect(jsonPath("$.content[0].changes.endpointUrl.to")
                        .value("https://as2-nuevo.socio.example:4443/as2"))
                .andExpect(jsonPath("$.content[0].changes.basicAuthPassword.to").value("(cambiada)"));

        // El alta deja el evento del socio y los de sus tres configuraciones, con el mismo usuario.
        mvc.perform(get("/api/audit-logs").param("partnerId", id).param("actor", "OPERADOR1"))
                .andExpect(jsonPath("$.content", hasSize(4)));
    }

    @Test
    void busquedaPorMessageId_losTresEventosEnOrdenCronologico() throws Exception {
        String msg = "<20261020.42@as2.example.com>";
        internal("""
            {"eventType": "DOCUMENT_SENT", "status": "SUCCESS", "source": "as2-adapter", "messageId": "%s"}
            """.formatted(msg));
        internal("""
            {"eventType": "MDN_RECEIVED", "status": "SUCCESS", "source": "as2-adapter", "messageId": "%s"}
            """.formatted(msg));
        internal("""
            {"eventType": "MIC_VERIFIED", "status": "SUCCESS", "source": "as2-adapter", "messageId": "%s",
             "detail": "El MIC del MDN coincide"}
            """.formatted(msg));

        mvc.perform(get("/api/audit-logs").param("messageId", msg))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].eventType").value("DOCUMENT_SENT"))
                .andExpect(jsonPath("$.content[1].eventType").value("MDN_RECEIVED"))
                .andExpect(jsonPath("$.content[2].eventType").value("MIC_VERIFIED"));
    }

    @Test
    void registroInterno_validaElEvento() throws Exception {
        mvc.perform(post("/internal/audit-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\": \"documento enviado\", \"status\": \"SUCCESS\", \"source\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("MAYUSCULAS")));
        mvc.perform(post("/internal/audit-logs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\": \"DOCUMENT_SENT\", \"status\": \"QUIZAS\", \"source\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportacionCsv_conLosMismosFiltros() throws Exception {
        create("SOCIO-AUD-CSV");

        mvc.perform(get("/api/audit-logs/export").param("partnerAs2Name", "SOCIO-AUD-CSV")
                        .param("eventType", "PARTNER_CREATED"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"bitacora-")))
                .andExpect(result -> {
                    String csv = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                    org.assertj.core.api.Assertions.assertThat(csv.split("\r\n")).hasSize(2);
                    org.assertj.core.api.Assertions.assertThat(csv).contains("PARTNER_CREATED,SUCCESS,operador1");
                    org.assertj.core.api.Assertions.assertThat(csv).doesNotContain("secreto");
                });

        mvc.perform(get("/api/audit-logs/event-types"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("PARTNER_CREATED")));
    }

    @Test
    void laBitacoraEsDeSoloLectura_porHttp() throws Exception {
        mvc.perform(post("/api/audit-logs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
