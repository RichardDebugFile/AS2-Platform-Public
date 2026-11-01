package edi.documents.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edi.documents.x12.Samples;
import edi.documents.x12.X12Document;
import edi.documents.x12.X12Parser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** HU-12 por HTTP sobre H2, con la migracion de Flyway aplicada (esquemas de 850, 997 y 889). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:x12api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class X12ApiIntegrationTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-01T14:30:00Z"), ZoneId.of("America/Guayaquil"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void interpreta_un850() throws Exception {
        mvc.perform(post("/api/documents/x12/parse").contentType(MediaType.TEXT_PLAIN).content(Samples.po850Crlf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCode").value("850"))
                .andExpect(jsonPath("$.version").value("4010"))
                .andExpect(jsonPath("$.sender.qualifier").value("12"))
                .andExpect(jsonPath("$.receiver.id").value("ID-EMPRESA"))
                .andExpect(jsonPath("$.controls.ISA13").value("000000123"))
                .andExpect(jsonPath("$.controls.GS06").value("456"))
                .andExpect(jsonPath("$.controls.ST02").value("0789"))
                .andExpect(jsonPath("$.segmentTerminator").value("\r\n"))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    void textoSinIsa_esUnknown() throws Exception {
        mvc.perform(post("/api/documents/x12/parse").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCode").value("UNKNOWN"))
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    void valida_contraElEsquemaDeLaMigracion() throws Exception {
        mvc.perform(post("/api/documents/x12/validate").contentType(MediaType.TEXT_PLAIN).content(Samples.po850()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        String sinCtt = Samples.po850().replace("CTT*2~\n", "").replace("SE*11*", "SE*10*");
        mvc.perform(post("/api/documents/x12/validate").contentType(MediaType.TEXT_PLAIN).content(sinCtt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.missingSegments[0]").value("CTT"));
    }

    @Test
    void listaLosEsquemas() throws Exception {
        mvc.perform(get("/api/documents/x12/schemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.typeCode == '850')].requiredSegments[3]").value("BEG"));
    }

    @Test
    void genera_un850ConValoresPorDefecto() throws Exception {
        String body = """
                {"type":"850","controlNumber":15,
                 "sender":{"qualifier":"08","id":"ID-EMPRESA"},"receiver":{"qualifier":"12","id":"ID-SOCIO"},
                 "purchaseOrder":{"number":"OC-9","buyer":{"name":"Comprador Ejemplo","id":"COMP-01"},
                   "seller":{"name":"Proveedor Ejemplo","id":"PROV-01"},
                   "lines":[{"quantity":10,"unitPrice":2.5,"productId":"ROSA-ROJA-60"}]}}""";

        String text = mvc.perform(post("/api/documents/x12/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        X12Document d = X12Parser.parse(text);
        assertThat(d.typeCode()).isEqualTo("850");
        assertThat(d.version()).isEqualTo("4010");
        assertThat(d.usageIndicator()).isEqualTo("T");
        assertThat(d.interchangeControl()).isEqualTo("000000015");
        assertThat(d.first("BEG").orElseThrow().element(5)).isEqualTo("20261101");
        assertThat(d.first("DTM").orElseThrow().element(2)).isEqualTo("20261108");
        assertThat(d.first("PO1").orElseThrow().element(3)).isEqualTo("EA");
        assertThat(d.interchangeTime()).isEqualTo("0930");
    }

    @Test
    void genera_el997DeUnDocumento_y_un889() throws Exception {
        String ack = mvc.perform(post("/api/documents/x12/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"997\",\"controlNumber\":16,\"original\":"
                                + quoted(Samples.po850()) + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        X12Document d = X12Parser.parse(ack);
        assertThat(d.typeCode()).isEqualTo("997");
        assertThat(d.sender().id()).isEqualTo("ID-EMPRESA");

        String promo = mvc.perform(post("/api/documents/x12/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"889","sender":{"qualifier":"08","id":"ID-EMPRESA"},
                                 "receiver":{"qualifier":"12","id":"ID-SOCIO"},
                                 "promotion":{"id":"PROMO-NOV","days":10,"vendor":{"name":"Proveedor Ejemplo","id":"PROV-01"}}}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        X12Document p = X12Parser.parse(promo);
        assertThat(p.typeCode()).isEqualTo("889");
        assertThat(p.segments().stream().filter(s -> s.id().equals("DTM")).map(s -> s.element(2)).toList())
                .containsExactly("20261101", "20261111");
    }

    @Test
    void tipoNoAdmitido_400() throws Exception {
        mvc.perform(post("/api/documents/x12/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"810\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_TYPE"));
    }

    @Test
    void datosQueFaltan_400ConElCampo() throws Exception {
        mvc.perform(post("/api/documents/x12/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"850\",\"sender\":{\"qualifier\":\"08\",\"id\":\"A\"},"
                                + "\"receiver\":{\"qualifier\":\"12\",\"id\":\"B\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Falta purchaseOrder"));
    }

    @Test
    void sinCuerpo_400() throws Exception {
        mvc.perform(post("/api/documents/x12/parse").contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isBadRequest());
    }

    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
