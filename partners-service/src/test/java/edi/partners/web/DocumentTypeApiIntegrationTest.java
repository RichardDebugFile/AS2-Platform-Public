package edi.partners.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edi.partners.config.RequestIdentity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Criterios de aceptacion de HU-13 de punta a punta por HTTP (servicio completo sobre H2). */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:doctypes;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "partners.storage.root=target/test-storage/doctypes", "partners.shared.root=target/test-shared/doctypes"
})
@AutoConfigureMockMvc
class DocumentTypeApiIntegrationTest {

    private static final String CATALOG = "/api/catalogs/document-types";

    @Autowired
    private MockMvc mvc;

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            String body) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).header(RequestIdentity.USER_HEADER, "operador1")
                .content(body));
    }

    private String type(String code, String version, String... responseIds) throws Exception {
        String responses = responseIds.length == 0 ? "null"
                : "[" + String.join(",", java.util.Arrays.stream(responseIds).map(id -> "\"" + id + "\"").toList()) + "]";
        String body = send(post(CATALOG), """
                {"standard": "X12", "code": "%s", "version": "%s", "name": "Tipo %s", "responseTypeIds": %s}
                """.formatted(code, version, code, responses))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String partner(String as2Name) throws Exception {
        String body = send(post("/api/partners/complete"),
                "{\"partner\": " + PartnerApiIntegrationTest.partnerJson(as2Name) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.partner.id");
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    @Test
    void catalogoSinDuplicados_mismoEstandarCodigoYVersionEs409() throws Exception {
        String v = "40" + unique();
        type("850", v);

        send(post(CATALOG), """
                {"standard": "X12", "code": "850", "version": "%s", "name": "Otra orden"}
                """.formatted(v))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Ya existe X12 850 " + v + " en el catalogo"));
        // Otra version del mismo codigo si se admite.
        type("850", v + "1");
    }

    @Test
    void asignacionPorSocio_laPestanaMuestraLosTresConSuDireccion() throws Exception {
        String v = "41" + unique();
        String t850 = type("850", v);
        String t856 = type("856", v);
        String t810 = type("810", v);
        String socio = partner("SOCIO-HU13-" + unique());

        send(put("/api/partners/" + socio + "/document-types"), """
                {"documentTypes": [
                  {"documentTypeId": "%s", "direction": "INBOUND", "extraFields": {"Codigo de proveedor": "PRV-1"}},
                  {"documentTypeId": "%s", "direction": "OUTBOUND"},
                  {"documentTypeId": "%s", "direction": "OUTBOUND", "notes": "Una por despacho"}
                ]}
                """.formatted(t850, t856, t810))
                .andExpect(status().isOk());

        mvc.perform(get("/api/partners/" + socio + "/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.documentType.code == '850')].direction").value("INBOUND"))
                .andExpect(jsonPath("$[?(@.documentType.code == '850')].extraFields['Codigo de proveedor']")
                        .value("PRV-1"))
                .andExpect(jsonPath("$[?(@.documentType.code == '856')].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$[?(@.documentType.code == '810')].notes").value("Una por despacho"));

        // Guardar de nuevo reemplaza la lista; un tipo repetido con la misma direccion es 400.
        send(put("/api/partners/" + socio + "/document-types"), """
                {"documentTypes": [
                  {"documentTypeId": "%s", "direction": "INBOUND"},
                  {"documentTypeId": "%s", "direction": "INBOUND"}
                ]}
                """.formatted(t850, t850))
                .andExpect(status().isBadRequest());
        send(put("/api/partners/" + socio + "/document-types"), """
                {"documentTypes": [{"documentTypeId": "%s", "direction": "INBOUND"}]}
                """.formatted(t850))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/audit-logs").param("partnerId", socio).param("eventType", "PARTNER_DOCUMENT_TYPES_UPDATED"))
                .andExpect(jsonPath("$.content[0].changes.documentTypes.to").value("X12 850 " + v + " INBOUND"));
    }

    @Test
    void correlacion_alConsultarEl850SeVeEl856ComoRespuestaEsperada() throws Exception {
        String v = "42" + unique();
        String t856 = type("856", v);
        String t850 = type("850", v, t856);

        mvc.perform(get(CATALOG + "/" + t850))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses[0].code").value("856"))
                .andExpect(jsonPath("$.responses[0].id").value(t856));
        mvc.perform(get(CATALOG))
                .andExpect(jsonPath("$[?(@.id == '" + t850 + "')].responses[0].code").value(hasItem("856")));

        // Un tipo no puede ser respuesta de si mismo.
        send(put(CATALOG + "/" + t850), """
                {"standard": "X12", "code": "850", "version": "%s", "name": "Orden", "responseTypeIds": ["%s"]}
                """.formatted(v, t850))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borradoProtegido_unTipoAsignadoEs409ConLosSociosQueLoUsan() throws Exception {
        String v = "43" + unique();
        String t850 = type("850", v);
        String t997 = type("997", v);
        String expecting = type("855", v, t997);
        String as2Name = "SOCIO-BORRADO-" + unique();
        String socio = partner(as2Name);
        send(put("/api/partners/" + socio + "/document-types"), """
                {"documentTypes": [{"documentTypeId": "%s", "direction": "INBOUND"}]}
                """.formatted(t850)).andExpect(status().isOk());

        mvc.perform(delete(CATALOG + "/" + t850))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_IN_USE"))
                .andExpect(jsonPath("$.message", containsString(as2Name)));

        // Sin socios se borra, y deja de figurar como respuesta esperada de otro.
        mvc.perform(delete(CATALOG + "/" + t997)).andExpect(status().isNoContent());
        mvc.perform(get(CATALOG + "/" + expecting)).andExpect(jsonPath("$.responses.length()").value(0));
        mvc.perform(get(CATALOG + "/" + t997)).andExpect(status().isNotFound());
    }
}
