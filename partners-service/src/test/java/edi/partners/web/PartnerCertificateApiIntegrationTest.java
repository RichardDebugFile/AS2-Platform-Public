package edi.partners.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edi.partners.config.RequestIdentity;
import edi.partners.testsupport.TestCertificates;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Criterios de aceptacion de HU-11 de punta a punta por HTTP, sobre H2. */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:certapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
class PartnerCertificateApiIntegrationTest {

    @TempDir
    static Path disk;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("partners.storage.root", () -> disk.resolve("partners").toString());
        r.add("partners.shared.root", () -> disk.resolve("erp").toString());
    }

    @Autowired
    private MockMvc mvc;

    private String create(String as2Name) throws Exception {
        String body = mvc.perform(post("/api/partners/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partner\": " + PartnerApiIntegrationTest.partnerJson(as2Name) + "}")
                        .header(RequestIdentity.USER_HEADER, "admin"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.partner.id");
    }

    private ResultActions upload(String partnerId, String usage, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/partners/" + partnerId + "/certificates/upload")
                .file(new MockMultipartFile("file", "socio.pem", "application/x-pem-file", content))
                .param("keyUsage", usage)
                .header(RequestIdentity.USER_HEADER, "operador1"));
    }

    private static String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.certificate.id");
    }

    @Test
    void rotacionSinCorte_ambosActivos_elNuevoPreferente_yBitacora() throws Exception {
        String p = create("SOCIO-C1");
        String c1 = id(upload(p, "ENCRYPTION", TestCertificates.cert("c1").pem())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.certificate.preferred").value(true)));

        String c2 = id(upload(p, "ENCRYPTION", TestCertificates.cert("c2").pem()).andExpect(status().isCreated()));

        mvc.perform(get("/api/partners/" + p + "/certificates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(c2))
                .andExpect(jsonPath("$[0].preferred").value(true))
                .andExpect(jsonPath("$[1].id").value(c1))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[1].preferred").value(false))
                .andExpect(jsonPath("$[1].pem").doesNotExist());
        mvc.perform(get("/api/audit-logs").param("partnerAs2Name", "SOCIO-C1").param("eventType", "CERTIFICATE_UPLOADED"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].actor").value("operador1"));
    }

    @Test
    void rechazos_conSuMotivo() throws Exception {
        String p = create("SOCIO-C2");

        upload(p, "ENCRYPTION", TestCertificates.cert("raiz").ca().pem())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El certificado es de una CA (no entidad final)"));
        upload(p, "ENCRYPTION", TestCertificates.cert("viejo").valid("2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z").pem())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El certificado ya está expirado"));
        upload(p, "ENCRYPTION", "basura".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        upload(p, "OTRO", TestCertificates.cert("x").pem()).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/partners/" + p + "/certificates/upload").param("keyUsage", "SSL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el archivo del certificado ('file')"));
        mvc.perform(get("/api/partners/" + p + "/certificates")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void resubida_yCambiosDeEstado() throws Exception {
        String p = create("SOCIO-C3");
        byte[] c1Pem = TestCertificates.cert("c1").pem();
        String c1 = id(upload(p, "SIGNATURE", c1Pem));
        String c2 = id(upload(p, "SIGNATURE", TestCertificates.cert("c2").pem()));

        mvc.perform(post("/api/partners/" + p + "/certificates/" + c1 + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameUsage", hasSize(2)))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
        upload(p, "SIGNATURE", c1Pem).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("REACTIVATED"));
        upload(p, "SIGNATURE", c1Pem).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("ALREADY_ACTIVE"));
        mvc.perform(post("/api/partners/" + p + "/certificates/" + c1 + "/prefer"))
                .andExpect(jsonPath("$.sameUsage[0].id").value(c1))
                .andExpect(jsonPath("$.sameUsage[0].preferred").value(true));
        mvc.perform(put("/api/partners/" + p + "/certificates/" + c2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Saliente\"}"))
                .andExpect(jsonPath("$.description").value("Saliente"));
        mvc.perform(delete("/api/partners/" + p + "/certificates/" + c2)).andExpect(status().isOk());
        mvc.perform(post("/api/partners/" + p + "/certificates/" + c1 + "/deactivate"))
                .andExpect(jsonPath("$.warnings[0]").value(startsWith("No queda ningun certificado de firma activo")));
    }

    @Test
    void detalle_pem_yMotor() throws Exception {
        String p = create("SOCIO-C4");
        String c1 = id(upload(p, "ENCRYPTION", TestCertificates.cert("c1").pem()));

        mvc.perform(get("/api/partners/" + p + "/certificates/" + c1))
                .andExpect(jsonPath("$.pem").value(startsWith("-----BEGIN CERTIFICATE-----")))
                .andExpect(jsonPath("$.issuedTo").value("c1"));
        mvc.perform(get("/api/partners/" + p + "/certificates/" + c1 + "/pem"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"SOCIO-C4_encryption_")));
        mvc.perform(get("/internal/partners/" + p + "/certificates").param("usage", "ENCRYPTION"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].partnerAs2Name").value("SOCIO-C4"));
        mvc.perform(get("/internal/certificates/" + c1)).andExpect(jsonPath("$.keyUsage").value("ENCRYPTION"));
        mvc.perform(get("/internal/certificates/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_NOT_FOUND"));
    }

    @Test
    void certificadoDeOtroSocio_noSeEncuentra() throws Exception {
        String a = create("SOCIO-C5");
        String b = create("SOCIO-C6");
        String c = id(upload(a, "SSL", TestCertificates.cert("tls").pem()));

        mvc.perform(get("/api/partners/" + b + "/certificates/" + c)).andExpect(status().isNotFound());
    }

    @Test
    void borrarElSocio_borraSusCertificados() throws Exception {
        String p = create("SOCIO-C7");
        String c = id(upload(p, "ENCRYPTION", TestCertificates.cert("c").pem()));
        mvc.perform(delete("/api/partners/" + p)).andExpect(status().is2xxSuccessful());

        mvc.perform(delete("/api/partners/" + p + "/purge").param("confirm", "SOCIO-C7"))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get("/internal/certificates/" + c)).andExpect(status().isNotFound());
    }
}
