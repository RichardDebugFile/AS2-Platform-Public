package edi.documents.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edi.documents.ingest.PartnerCopyRecovery;
import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentMdnRepository;
import edi.documents.store.DocumentRepository;
import edi.documents.store.DocumentStatus;
import edi.documents.x12.Samples;
import edi.documents.x12.X12Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Escenarios de aceptacion de HU-14 por HTTP, sobre H2 y un disco temporal. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:repo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    // La bitacora no esta: los eventos quedan en el log sin afectar la ingesta.
    "documents.audit.url=http://localhost:9"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class DocumentRepositoryApiIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    @TempDir
    static Path disk;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    DocumentRepository documents;

    @Autowired
    DocumentMdnRepository mdns;

    @Autowired
    PartnerCopyRecovery recovery;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("documents.storage.root", () -> disk.resolve("as2_data").toString());
        r.add("documents.partners.root", () -> disk.resolve("as2_data/partners").toString());
    }

    @BeforeAll
    static void partners() throws Exception {
        for (String p : List.of("SOCIO-A", "SOCIO-C", "SOCIO-D")) {
            for (String sub : List.of("Incoming", "Archive", "Error")) {
                Files.createDirectories(disk.resolve("as2_data/partners").resolve(p).resolve(sub));
            }
        }
        // SOCIO-B: registrado, pero su Incoming (enlace a la carpeta compartida) no esta disponible.
        Files.createDirectories(disk.resolve("as2_data/partners/SOCIO-B"));
    }

    @Test
    void registraAcuseDelEnvioEnBaseYDiscoConActualizacionIdempotente() throws Exception {
        String id = body(ingest("SOCIO-C", Direction.OUTBOUND, "<ENVIO@example.com>", Samples.po850())
                .andExpect(status().isCreated())).path("document").path("id").asString();
        String update = """
                {
                    "status":"MDN_RECEIVED",
                    "receipt":{
                        "disposition":"automatic-action/MDN-sent-automatically; processed",
                        "receivedMic":"QUJD",
                        "micMatches":true,
                        "signatureValid":true,
                        "rawMdn":"Content-Type: multipart/signed"
                    }
                }
                """;
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/internal/as2/documents/{id}/result", id)
                    .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MDN_RECEIVED"));
        mvc.perform(get("/api/documents/{id}/mdn", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.micMatches").value(true))
                .andExpect(jsonPath("$.signatureValid").value(true));
        assertThat(disk.resolve("as2_data/mdn").resolve(id + ".mdn")).exists()
                .hasContent("Content-Type: multipart/signed");
    }

    @Test
    void conservaElMdnBinarioEnDiscoYCodificaLaVistaParaLaBase() throws Exception {
        String id = body(ingest("SOCIO-C", Direction.OUTBOUND, "<MDN-BINARIO@example.com>", Samples.po850())
                .andExpect(status().isCreated())).path("document").path("id").asString();
        String raw = "Content-Type: multipart/signed\r\n\r\n" + (char) 0 + (char) 128;
        byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
        String result = json.writeValueAsString(Map.of("status", "MDN_RECEIVED", "receipt",
                Map.of("disposition", "processed", "rawMdn", raw)));
        mvc.perform(put("/internal/as2/documents/{id}/result", id)
                .contentType(MediaType.APPLICATION_JSON).content(result)).andExpect(status().isNoContent());
        assertThat(mdns.findById(UUID.fromString(id)).orElseThrow().getRawMdn())
                .isEqualTo("Base64:\n" + Base64.getEncoder().encodeToString(bytes));
        assertThat(disk.resolve("as2_data/mdn").resolve(id + ".mdn")).exists().hasBinaryContent(bytes);
    }

    @Test
    void rechazaCompletarUnEnvioConDatosInvalidosOSobrescribirSuResultado() throws Exception {
        String id = body(ingest("SOCIO-C", Direction.OUTBOUND, "<ESTADO-ENVIO@example.com>", Samples.po850())
                .andExpect(status().isCreated())).path("document").path("id").asString();
        for (String invalid : List.of("MDN_RECEIVED", "CREATED")) {
            String update = json.writeValueAsString(Map.of("status", invalid));
            mvc.perform(put("/internal/as2/documents/{id}/result", id)
                    .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/internal/as2/documents/{id}/result", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/internal/as2/documents/{id}/result", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SEND_FAILED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void genera997ConEmisorYReceptorInvertidosYLosControlesOriginales() throws Exception {
        String original = Samples.po850();
        String ack = mvc.perform(post("/internal/as2/ack997").contentType(MediaType.TEXT_PLAIN).content(original))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var request = X12Parser.parse(original);
        var response = X12Parser.parse(ack);
        assertThat(response.typeCode()).isEqualTo("997");
        assertThat(response.sender()).isEqualTo(request.receiver());
        assertThat(response.receiver()).isEqualTo(request.sender());
        assertThat(ack).contains("AK1*" + request.functionalCode() + "*" + request.groupControl(),
                "AK2*" + request.typeCode() + "*" + request.transactionControl());
    }

    private ResultActions ingest(String partner, Direction direction, String messageId, String body) throws Exception {
        var req = post("/api/documents/ingest/as2").param("partner", partner).param("direction", direction.name())
                .contentType("application/edi-x12").content(body.getBytes(StandardCharsets.ISO_8859_1))
                .header("AS2-From", "ID-SOCIO").header("AS2-To", "ID-EMPRESA").header("X-User-Name", "ana.operadora");
        if (messageId != null) {
            req.header("Message-ID", messageId);
        }
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void ingestaDeUnDocumentoDeEntrada() throws Exception {
        JsonNode doc = body(ingest("SOCIO-A", Direction.INBOUND, "<M1@example.com>", Samples.po850())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))).get("document");

        assertThat(doc.get("status").asString()).isEqualTo("RECEIVED");
        assertThat(doc.get("typeCode").asString()).isEqualTo("850");
        assertThat(doc.get("isaControl").asString()).isEqualTo("000000123");
        assertThat(doc.get("gsControl").asString()).isEqualTo("456");
        assertThat(doc.get("stControl").asString()).isEqualTo("0789");
        assertThat(doc.get("createdBy").asString()).isEqualTo("ana.operadora");
        assertThat(doc.get("partnerCopyStatus").asString()).isEqualTo("COPIED");

        String day = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZONE));
        Path dir = disk.resolve("as2_data/inbound/success").resolve(day).resolve("850/SOCIO-A");
        Path file = dir.resolve(doc.get("fileName").asString());
        assertThat(file).exists().hasBinaryContent(Samples.po850().getBytes(StandardCharsets.ISO_8859_1));
        assertThat(doc.get("fileName").asString()).startsWith("EDI_850_4010_000000123_456_0789_");
        assertThat(disk.resolve("as2_data/partners/SOCIO-A/Incoming").resolve(file.getFileName())).exists();

        mvc.perform(get("/api/documents/{id}/storage-status", doc.get("id").asString()))
                .andExpect(jsonPath("$.database").value(true))
                .andExpect(jsonPath("$.repositoryFile").value(true))
                .andExpect(jsonPath("$.partnerCopy").value(true));
    }

    @Test
    void reintentoDelSocio_noCreaOtroDocumento() throws Exception {
        String first = body(ingest("SOCIO-A", Direction.INBOUND, "<M2@example.com>", Samples.po850())
                .andExpect(status().isCreated())).get("document").get("id").asString();

        ingest("SOCIO-A", Direction.INBOUND, "<M2@example.com>", Samples.po850())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"))
                .andExpect(jsonPath("$.document.id").value(first));
        assertThat(documents.findAll().stream().filter(d -> d.getMessageId().equals("<M2@example.com>")))
                .hasSize(1);
    }

    @Test
    void atomicidad_siNoSePuedeEscribirElArchivoNoQuedaRegistro() throws Exception {
        // Un archivo donde deberia ir la carpeta: el repositorio no puede crear inbound/failed/...
        Files.createDirectories(disk.resolve("as2_data/inbound"));
        Files.writeString(disk.resolve("as2_data/inbound/failed"), "no es una carpeta");
        String invalido = Samples.po850().replace("BEG*00*SA*OC-2026-001**20261101~\n", "");

        ingest("SOCIO-D", Direction.INBOUND, "<M3@example.com>", invalido)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));
        assertThat(documents.findByMessageId("<M3@example.com>")).isEmpty();
    }

    @Test
    void recuperacionTrasCaidaDeLaCarpetaCompartida(CapturedOutput output) throws Exception {
        JsonNode doc = body(ingest("SOCIO-B", Direction.INBOUND, "<M4@example.com>", Samples.po850())
                .andExpect(status().isCreated())).get("document");
        assertThat(doc.get("partnerCopyStatus").asString()).isEqualTo("PENDING");
        Path incoming = disk.resolve("as2_data/partners/SOCIO-B/Incoming");

        Files.createDirectories(incoming); // vuelve la carpeta compartida
        assertThat(recovery.recover()).isGreaterThanOrEqualTo(1);

        assertThat(incoming.resolve(doc.get("fileName").asString())).exists();
        assertThat(output).contains("Copia restaurada en la carpeta del socio: socio=SOCIO-B messageId=<M4@example.com>");
        mvc.perform(get("/api/documents/{id}", doc.get("id").asString()))
                .andExpect(jsonPath("$.partnerCopyStatus").value("COPIED"));
    }

    @Test
    void busqueda_500DocumentosDe3Socios_filtraYPaginaEnMenosDeUnSegundo() throws Exception {
        Instant now = Instant.now();
        List<Document> batch = new ArrayList<>();
        String[] partners = {"BUSQ-A", "BUSQ-B", "BUSQ-C"};
        String[] types = {"850", "997", "889"};
        int expected = 0;
        for (int i = 0; i < 500; i++) {
            String partner = partners[i % 3];
            String type = types[(i / 3) % 3];
            boolean recent = i % 5 != 0; // uno de cada cinco, de hace un mes
            Document d = new Document(UUID.randomUUID(), partner, Direction.INBOUND, "<B" + i + "@example.com>",
                    recent ? now.minus(i % 6, ChronoUnit.DAYS) : now.minus(30, ChronoUnit.DAYS));
            d.describe(type, "4010", String.format("%09d", i), String.valueOf(i), String.format("%04d", i));
            d.content(100, "0".repeat(64), null, "prueba");
            d.status(DocumentStatus.RECEIVED, null);
            d.storedAt(disk.resolve("as2_data/inbound/success/x/" + i + ".edi").toString());
            batch.add(d);
            if (partner.equals("BUSQ-A") && type.equals("850") && recent) {
                expected++;
            }
        }
        documents.saveAll(batch);

        long start = System.nanoTime();
        JsonNode page = body(mvc.perform(get("/api/documents/search").param("partner", "BUSQ-A").param("type", "850")
                        .param("from", LocalDate.now(ZONE).minusDays(7).toString()).param("size", "20"))
                .andExpect(status().isOk()));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(millis).isLessThan(1000);
        assertThat(page.get("totalElements").asLong()).isEqualTo(expected);
        assertThat(page.get("content")).hasSizeLessThanOrEqualTo(20);
        for (JsonNode d : page.get("content")) {
            assertThat(d.get("partnerAs2Name").asString()).isEqualTo("BUSQ-A");
            assertThat(d.get("typeCode").asString()).isEqualTo("850");
        }
        // Sin archivo en disco: se retiran para no ensuciar el control de consistencia.
        documents.deleteAll(batch);
    }

    @Test
    void consistencia_reportaElArchivoBorradoAMano_yLosHuerfanos() throws Exception {
        JsonNode doc = body(ingest("SOCIO-C", Direction.INBOUND, "<M5@example.com>", Samples.po850())
                .andExpect(status().isCreated())).get("document");
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZONE));
        Path dir = disk.resolve("as2_data/inbound/success").resolve(day).resolve("850/SOCIO-C");
        Files.delete(dir.resolve(doc.get("fileName").asString()));
        Files.writeString(dir.resolve("EDI_HUERFANO.edi"), "sin registro");

        mvc.perform(get("/api/consistency/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistent").value(false))
                .andExpect(jsonPath("$.missingFiles[?(@.messageId == '<M5@example.com>')]").exists())
                .andExpect(jsonPath("$.orphanFiles[?(@ =~ /.*EDI_HUERFANO.edi/)]").exists());
    }

    @Test
    void documentoDeSalida_vaAArchive_yUnoInvalidoDeSalidaAError() throws Exception {
        JsonNode out = body(ingest("SOCIO-C", Direction.OUTBOUND, null, Samples.ack997())
                .andExpect(status().isCreated())).get("document");
        assertThat(out.get("status").asString()).isEqualTo("CREATED");
        assertThat(out.get("messageId").asString()).endsWith("@as2-platform>");
        assertThat(disk.resolve("as2_data/partners/SOCIO-C/Archive").resolve(out.get("fileName").asString())).exists();

        JsonNode bad = body(ingest("SOCIO-C", Direction.OUTBOUND, "<M6@example.com>", "texto sin ISA")
                .andExpect(status().isCreated())).get("document");
        assertThat(bad.get("status").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(bad.get("typeCode").asString()).isEqualTo("UNKNOWN");
        assertThat(disk.resolve("as2_data/partners/SOCIO-C/Error").resolve(bad.get("fileName").asString())).exists();
    }

    @Test
    void busqueda_porParteDelSocioMessageIdEIsa_sinDistinguirMayusculas() throws Exception {
        JsonNode doc = body(ingest("SOCIO-A", Direction.INBOUND, "<Parcial-105@example.com>", Samples.po850())
                .andExpect(status().isCreated())).get("document");
        String isa = doc.get("isaControl").asString();

        mvc.perform(get("/api/documents/search").param("partner", "ocio-a").param("messageId", "parcial-105"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].messageId").value("<Parcial-105@example.com>"));
        mvc.perform(get("/api/documents/search").param("messageId", "PARCIAL-105")
                        .param("isa", isa.substring(isa.length() - 3)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/documents/search").param("partner", "socio-b").param("messageId", "parcial-105"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // "%" se busca como texto: no es un comodin que traiga todo.
        mvc.perform(get("/api/documents/search").param("messageId", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // En el tablero el socio se elige de una lista y se compara completo.
        mvc.perform(get("/api/documents/dashboard").param("partner", "socio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void detalle_contenido_descarga_mdn_estadisticas_y_borrado() throws Exception {
        String id = body(ingest("SOCIO-A", Direction.INBOUND, "<M7@example.com>", Samples.po850())
                .andExpect(status().isCreated())).get("document").get("id").asString();

        mvc.perform(get("/api/documents/by-message-id").param("messageId", "<M7@example.com>"))
                .andExpect(jsonPath("$.id").value(id));
        String payload = mvc.perform(get("/api/documents/{id}/payload", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.ISO_8859_1);
        assertThat(payload).isEqualTo(Samples.po850());
        mvc.perform(get("/api/documents/{id}/content", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mvc.perform(get("/api/documents/{id}/mdn", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mvc.perform(get("/api/documents/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.lastDays.length()").value(7));
        mvc.perform(get("/api/documents/recent").param("limit", "1")).andExpect(jsonPath("$.length()").value(1));

        String fileName = documents.findById(UUID.fromString(id)).orElseThrow().getFilePath();
        mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isNotFound());
        assertThat(Path.of(fileName)).doesNotExist();
    }

    @Test
    void tablero_cuentaElRangoPorSocioYDia_yRechazaRangosInvalidos() throws Exception {
        ingest("SOCIO-A", Direction.INBOUND, "<T1@example.com>", Samples.po850()).andExpect(status().isCreated());

        mvc.perform(get("/api/documents/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.byDay.length()").value(7))
                .andExpect(jsonPath("$.total").isNumber());
        mvc.perform(get("/api/documents/dashboard").param("partner", "socio-a").param("direction", "INBOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byPartner.length()").value(1))
                .andExpect(jsonPath("$.byPartner[0].partner").value("SOCIO-A"))
                .andExpect(jsonPath("$.byDirection.OUTBOUND").doesNotExist());
        mvc.perform(get("/api/documents/dashboard").param("from", "2026-11-10").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/documents/dashboard").param("from", "2024-01-01").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void socioNoRegistrado_vacio_oDireccionInvalida_son400() throws Exception {
        ingest("NO-EXISTE", Direction.INBOUND, "<M8@example.com>", Samples.po850())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARTNER_UNKNOWN"));
        ingest("SOCIO-A", Direction.INBOUND, "<M9@example.com>", "")
                .andExpect(status().isBadRequest());
        ingest("../SOCIO-A", Direction.INBOUND, "<M10@example.com>", Samples.po850())
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents/ingest/as2").param("partner", "SOCIO-A").param("direction", "LATERAL")
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isBadRequest());
        try (Stream<Path> s = Files.walk(disk.resolve("as2_data/partners"))) {
            assertThat(s.filter(p -> p.toString().contains("NO-EXISTE"))).isEmpty();
        }
    }
}
