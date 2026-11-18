package edi.alerts.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edi.alerts.audit.AuditClient;
import edi.alerts.audit.AuditEvent;
import edi.alerts.web.SmtpConfigController;
import edi.alerts.testsupport.FakeSmtpServer;
import edi.alerts.testsupport.TestKeys;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** AS2-106 de punta a punta por HTTP: configuracion SMTP, contrasena protegida y correo de prueba. */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:smtpapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "alerts.mail.timeout-ms=3000"
})
@AutoConfigureMockMvc
class SmtpConfigApiIntegrationTest {

    private static final String PASSWORD = "clave-smtp-secreta";

    /** Clave de cifrado generada al correr: el repositorio no guarda ninguna clave. */
    @DynamicPropertySource
    static void secretKey(DynamicPropertyRegistry registry) {
        String key = TestKeys.aes256();
        registry.add("alerts.secret-key", () -> key);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SmtpConfigRepository repo;

    @MockitoBean
    private AuditClient audit;

    private FakeSmtpServer smtp;

    @BeforeEach
    void setUp() throws Exception {
        repo.deleteAll();
        smtp = new FakeSmtpServer(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        smtp.close();
    }

    private static String config(int port, String username, String password) {
        return """
            {"host": "127.0.0.1", "port": %d, "security": "NONE", "username": %s, "password": %s,
             "fromAddress": "alertas@example.com", "fromName": "Plataforma AS2/EDI"}
            """.formatted(port, json(username), json(password));
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/smtp-config").contentType(MediaType.APPLICATION_JSON)
                .header(SmtpConfigController.USER_HEADER, "ricardo").content(body));
    }

    private ResultActions sendTest(String to) throws Exception {
        return mvc.perform(post("/api/smtp-config/test").contentType(MediaType.APPLICATION_JSON)
                .header(SmtpConfigController.USER_HEADER, "ricardo").content("{\"to\": \"" + to + "\"}"));
    }

    private List<AuditEvent> auditEvents() {
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).publish(events.capture());
        return events.getAllValues();
    }

    @Test
    void sinConfiguracion_loDice_yLaPruebaPideGuardarlaPrimero() throws Exception {
        mvc.perform(get("/api/smtp-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.passwordSet").value(false));
        sendTest("ricardo@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SMTP_NOT_CONFIGURED"));
    }

    @Test
    void guardar_cifraLaContrasena_yNuncaLaDevuelve() throws Exception {
        save(config(2525, "alertas@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.host").value("127.0.0.1"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.updatedBy").value("ricardo"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = repo.findById(SmtpConfig.SINGLETON_ID).orElseThrow().getPasswordEnc();
        assertThat(stored).startsWith("gcm:").doesNotContain(PASSWORD);
        String body = mvc.perform(get("/api/smtp-config")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain(stored);

        // Sin contrasena en la peticion se conserva la guardada.
        save(config(2525, "alertas@example.com", null)).andExpect(jsonPath("$.passwordSet").value(true));
        assertThat(repo.findById(SmtpConfig.SINGLETON_ID).orElseThrow().getPasswordEnc()).isEqualTo(stored);

        // La bitacora registra el cambio sin la contrasena.
        AuditEvent first = auditEvents().getFirst();
        assertThat(first.eventType()).isEqualTo("SMTP_CONFIG_UPDATED");
        assertThat(first.actor()).isEqualTo("ricardo");
        assertThat(first.changes().get("password").to()).isEqualTo("(cambiada)");
        assertThat(first.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void usuarioSinContrasena_yDatosInvalidos_son400() throws Exception {
        save(config(2525, "alertas@example.com", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Con usuario SMTP hace falta la contrasena"));
        save("""
            {"host": "smtp con espacios", "port": 70000, "security": "NONE", "fromAddress": "no-es-correo"}
            """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertThat(repo.count()).isZero();
    }

    @Test
    void correoDePrueba_llegaAlServidorConRemitenteYAsunto() throws Exception {
        save(config(smtp.port(), null, null)).andExpect(status().isOk());
        clearInvocations(audit);

        sendTest("ricardo@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.to").value("ricardo@example.com"))
                .andExpect(jsonPath("$.server").value("127.0.0.1:" + smtp.port()));

        assertThat(smtp.received()).hasSize(1);
        FakeSmtpServer.Received mail = smtp.received().getFirst();
        assertThat(mail.mailFrom()).contains("alertas@example.com");
        assertThat(mail.rcptTo()).singleElement().asString().contains("ricardo@example.com");
        assertThat(mail.data()).contains("Subject: Prueba de correo de la Plataforma AS2/EDI");
        assertThat(auditEvents()).singleElement().satisfies(e -> {
            assertThat(e.eventType()).isEqualTo("SMTP_TEST_SENT");
            assertThat(e.success()).isTrue();
        });
    }

    @Test
    void servidorQueNoResponde_es502ConLaCausa_yQuedaEnLaBitacora() throws Exception {
        int closedPort = smtp.port();
        smtp.close();
        save(config(closedPort, null, null)).andExpect(status().isOk());
        clearInvocations(audit);

        sendTest("ricardo@example.com")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SMTP_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "No se pudo enviar el correo de prueba por 127.0.0.1:" + closedPort + ": ")));
        assertThat(auditEvents()).singleElement().satisfies(e -> {
            assertThat(e.eventType()).isEqualTo("SMTP_TEST_SENT");
            assertThat(e.success()).isFalse();
        });
    }

    @Test
    void contrasenaRechazada_loDiceClaro() throws Exception {
        try (FakeSmtpServer strict = new FakeSmtpServer(true)) {
            save(config(strict.port(), "alertas@example.com", "equivocada")).andExpect(status().isOk());

            sendTest("ricardo@example.com")
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith(
                            "el servidor rechazo el usuario o la contrasena")));
            assertThat(strict.received()).isEmpty();
        }
    }

    @Test
    void quitarElUsuario_borraLaContrasena() throws Exception {
        save(config(2525, "alertas@example.com", PASSWORD)).andExpect(jsonPath("$.passwordSet").value(true));
        save(config(2525, null, null)).andExpect(jsonPath("$.passwordSet").value(false))
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.nullValue()));
        assertThat(repo.findById(SmtpConfig.SINGLETON_ID).orElseThrow().hasPassword()).isFalse();
        verify(audit, atLeastOnce()).publish(any());
    }
}
