package edi.partners.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edi.partners.config.RequestIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Criterios de aceptacion de HU-09 de punta a punta por HTTP, sobre H2 y un disco temporal. */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:folders;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class PartnerFolderApiIntegrationTest {

    @TempDir
    static Path disk;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("partners.storage.root", () -> disk.resolve("partners").toString());
        r.add("partners.shared.root", () -> disk.resolve("erp").toString());
    }

    @Autowired
    private MockMvc mvc;

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(RequestIdentity.USER_HEADER, "admin"));
    }

    private String create(String as2Name) throws Exception {
        String body = postJson("/api/partners/complete", "{\"partner\": " + PartnerApiIntegrationTest.partnerJson(as2Name) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.partner.id");
    }

    @Test
    void alta_creaElArbolEstandarEnDisco() throws Exception {
        String id = create("SOCIO-F1");

        for (String sub : new String[] {"Archive", "Certs", "Error", "Incoming", "MDN", "Outgoing"}) {
            assertThat(disk.resolve("partners/SOCIO-F1").resolve(sub)).isDirectory();
        }
        mvc.perform(get("/api/partners/" + id + "/folders"))
                .andExpect(jsonPath("$.health").value("OK"))
                .andExpect(jsonPath("$.folderState").value("LOCAL"));
    }

    @Test
    void altaFallida_noDejaCarpetasSueltas() throws Exception {
        String sinUrl = "{\"partner\": " + PartnerApiIntegrationTest.partnerJson("SOCIO-F2") + ", \"mdn\": "
                + "{\"localFolder\": \"/x\", \"receiptType\": \"SIGNED\", \"deliveryMode\": \"ASYNC\"}}";

        postJson("/api/partners/complete", sinUrl).andExpect(status().isBadRequest());

        assertThat(disk.resolve("partners/SOCIO-F2")).doesNotExist();
    }

    @Test
    void precheckNegativo_luegoEnlaceYProduccion_conBitacora() throws Exception {
        String id = create("SOCIO-F3");
        Files.writeString(disk.resolve("partners/SOCIO-F3/Outgoing/pedido.edi"), "ISA*00");

        postJson("/api/partners/" + id + "/folders/precheck", "{\"sharedFolderName\": \"SocioF3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.problems[0]", containsString("no existe en el montaje")));
        postJson("/api/partners/" + id + "/folders/link", "{\"sharedFolderName\": \"SocioF3\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLDERS_PRECHECK_FAILED"));

        Files.createDirectories(disk.resolve("erp/SocioF3"));
        postJson("/api/partners/" + id + "/folders/precheck", "{\"sharedFolderName\": \"SocioF3\"}")
                .andExpect(jsonPath("$.ok").value(true));
        postJson("/api/partners/" + id + "/folders/link", "{\"sharedFolderName\": \"SocioF3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderState").value("SAMBA_LINKED"))
                .andExpect(jsonPath("$.health").value("OK"));
        assertThat(Files.isSymbolicLink(disk.resolve("partners/SOCIO-F3/Outgoing"))).isTrue();
        assertThat(disk.resolve("erp/SocioF3/Outgoing/pedido.edi")).hasContent("ISA*00");

        postJson("/api/partners/" + id + "/production", "{\"enabled\": true, \"confirm\": \"SOCIO-F3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderState").value("PRODUCTION"))
                .andExpect(jsonPath("$.enableIncomingQueue").value(true));
        assertThat(disk.resolve("erp/SocioF3/Incoming_queue")).isDirectory();
        postJson("/api/partners/" + id + "/production", "{\"enabled\": false, \"confirm\": \"SOCIO-F3\"}")
                .andExpect(jsonPath("$.enableIncomingQueue").value(false));

        mvc.perform(get("/api/audit-logs").param("partnerId", id).param("eventType", "FOLDERS_LINKED"))
                .andExpect(jsonPath("$.content[0].actor").value("admin"))
                .andExpect(jsonPath("$.content[0].changes.sharedFolderName.to").value("SocioF3"));
        mvc.perform(get("/api/audit-logs").param("partnerId", id).param("eventType", "PRODUCTION_DISABLED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void montajeCaido_folderStatusesMuestraError_yElMotorDejaDeVigilarlo() throws Exception {
        String id = create("SOCIO-F4");
        Files.createDirectories(disk.resolve("erp/SocioF4"));
        postJson("/api/partners/" + id + "/folders/link", "{\"sharedFolderName\": \"SocioF4\"}").andExpect(status().isOk());
        mvc.perform(get("/internal/partners/filewatcher-enabled")).andExpect(jsonPath("$", hasItem("SOCIO-F4")));

        Files.move(disk.resolve("erp/SocioF4"), disk.resolve("desmontado-F4"));

        mvc.perform(get("/api/partners/folder-statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.as2Name == 'SOCIO-F4')].health").value("ERROR"));
        mvc.perform(get("/internal/partners/filewatcher-enabled")).andExpect(jsonPath("$", not(hasItem("SOCIO-F4"))));
    }

    @Test
    void conElSocioEnlazado_noSeCambiaSuCarpetaCompartidaPorLaEdicion() throws Exception {
        String id = create("SOCIO-F5");
        Files.createDirectories(disk.resolve("erp/SocioF5"));
        postJson("/api/partners/" + id + "/folders/link", "{\"sharedFolderName\": \"SocioF5\"}").andExpect(status().isOk());

        String edit = PartnerApiIntegrationTest.partnerJson("SOCIO-F5").replace("\"path\": \"/as2\",",
                "\"path\": \"/as2\", \"sharedFolderName\": \"Otra\",");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/partners/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("ya enlazado")));
    }
}
