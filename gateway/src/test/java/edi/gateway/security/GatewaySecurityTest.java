package edi.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Criterios de aceptacion de HU-05 sobre la seguridad en el borde.
 *
 * <p>Todas las rutas se reenvian ({@code forward:}) a un endpoint del propio test, asi el resultado
 * no depende de que servicios esten levantados: se comprueba si el gateway dejo pasar la peticion
 * (cualquier codigo distinto de 401/403) o la corto el.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(GatewaySecurityTest.RutasAlSumidero.class)
class GatewaySecurityTest {

    static final String ADMIN = "jwt.admin";
    static final String OPERADOR = "jwt.operador";
    static final String AUDITOR = "jwt.auditor";
    static final String CADUCADO = "jwt.caducado";

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder decoder;

    @LocalServerPort
    int port;

    @BeforeEach
    void tokens() {
        // BadJwtException es lo que lanza el decoder real ante firma invalida o token caducado, y
        // Spring Security la traduce a 401.
        when(decoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("token desconocido")));
        when(decoder.decode(CADUCADO)).thenReturn(Mono.error(new BadJwtException("Jwt expired")));
        when(decoder.decode(ADMIN)).thenReturn(Mono.just(jwt(ADMIN, "admin", "ADMIN")));
        when(decoder.decode(OPERADOR)).thenReturn(Mono.just(jwt(OPERADOR, "operador1", "OPERATOR")));
        when(decoder.decode(AUDITOR)).thenReturn(Mono.just(jwt(AUDITOR, "auditor1", "AUDITOR")));
    }

    private static Jwt jwt(String token, String user, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject(user)
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    /** Todas las rutas terminan en {@link Sumidero}, que responde 204 sin salir del proceso. */
    @TestConfiguration
    @Import(Sumidero.class)
    static class RutasAlSumidero {
        @Bean
        RouteLocator sumidero(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("sumidero", r -> r.order(-100)
                            .path("/**")
                            .filters(f -> f.setPath("/__sumidero"))
                            .uri("forward:/__sumidero"))
                    .build();
        }
    }

    @RestController
    static class Sumidero {
        @RequestMapping("/__sumidero")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void aceptar() {}
    }

    private static void dejoPasar(WebTestClient.ResponseSpec res) {
        HttpStatus status =
                HttpStatus.valueOf(res.returnResult(Void.class).getStatus().value());
        assertThat(status)
                .as("el gateway no debe cortar esta peticion (401/403)")
                .isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ---- Escenario: API protegida sin sesion ------------------------------------------------

    @Test
    void apiSinSesion_401() {
        client.get().uri("/api/partners").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/audit-logs").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/alerts/rules").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/documents").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/as2/send").exchange().expectStatus().isUnauthorized();
        client.get().uri("/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenCaducadoOInventado_401() {
        client.get().uri("/api/partners").cookie("access_token", CADUCADO).exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/partners").cookie("access_token", "lo.que.sea").exchange().expectStatus().isUnauthorized();
    }

    /** Si el JWKS no responde no es culpa del token: 500, no un 401 que mande al login. */
    @Test
    void jwksInaccesible_500() {
        when(decoder.decode("cualquiera")).thenReturn(Mono.error(new JwtException("JWKS no disponible")));
        client.get().uri("/api/partners").cookie("access_token", "cualquiera").exchange().expectStatus().is5xxServerError();
    }

    // ---- Escenario: API protegida con sesion ------------------------------------------------

    @Test
    void apiConCookieValida_pasa() {
        dejoPasar(client.get().uri("/api/partners").cookie("access_token", ADMIN).exchange());
    }

    /** Clientes API y scripts mandan la cabecera; tiene prioridad sobre la cookie. */
    @Test
    void apiConCabeceraBearer_pasa() {
        dejoPasar(client.get().uri("/api/partners").header("Authorization", "Bearer " + ADMIN).exchange());
    }

    // ---- Rutas publicas ---------------------------------------------------------------------

    @Test
    void loginSinSesion_pasa() {
        dejoPasar(client.post().uri("/auth/login").exchange());
    }

    /**
     * Escenario: renovacion con access caducado. El navegador manda la cookie caducada tambien al
     * renovar; si el gateway la inspeccionara, la sesion no podria renovarse nunca.
     */
    @Test
    void refreshYLogoutConCookieCaducada_pasan() {
        dejoPasar(client.post().uri("/auth/refresh").cookie("access_token", CADUCADO).exchange());
        dejoPasar(client.post().uri("/auth/logout").cookie("access_token", CADUCADO).exchange());
        dejoPasar(client.post().uri("/auth/mfa/verify").cookie("access_token", CADUCADO).exchange());
    }

    /** Escenario: registro no publico. */
    @Test
    void registerSinSesion_401() {
        client.post().uri("/auth/register").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void jwksYHealth_pasan() {
        dejoPasar(client.get().uri("/.well-known/jwks.json").exchange());
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    /** Escenario: recepcion AS2 publica. Los socios se autentican con S/MIME, no con JWT. */
    @Test
    void recepcionAs2YCallbackMdn_pasan() {
        dejoPasar(client.post().uri("/as2/receive").exchange());
        dejoPasar(client.post().uri("/internal/as2/mdn-callback").exchange());
    }

    @Test
    void preflightCors_pasa() {
        dejoPasar(client.options()
                .uri("/api/partners")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange());
    }

    // ---- Roles: AUDITOR solo lectura, configuracion SMTP solo ADMIN -------------------------

    @Test
    void auditor_consulta_pasa() {
        dejoPasar(client.get().uri("/api/partners").cookie("access_token", AUDITOR).exchange());
        dejoPasar(client.get().uri("/api/documents").cookie("access_token", AUDITOR).exchange());
        dejoPasar(client.get().uri("/api/audit-logs").cookie("access_token", AUDITOR).exchange());
    }

    @Test
    void auditor_modifica_403() {
        client.post().uri("/api/partners").cookie("access_token", AUDITOR).exchange().expectStatus().isForbidden();
        client.put().uri("/api/partners/x").cookie("access_token", AUDITOR).exchange().expectStatus().isForbidden();
        client.patch().uri("/api/partners/x/status").cookie("access_token", AUDITOR).exchange().expectStatus().isForbidden();
        client.delete().uri("/api/partners/x").header("Authorization", "Bearer " + AUDITOR).exchange().expectStatus().isForbidden();
        client.post().uri("/api/as2/send").cookie("access_token", AUDITOR).exchange().expectStatus().isForbidden();
    }

    /** Su propia sesion y su propio MFA no son "modificar la plataforma". */
    @Test
    void auditor_sesionYMfaPropios_pasan() {
        dejoPasar(client.post().uri("/users/me/mfa/setup").cookie("access_token", AUDITOR).exchange());
    }

    @Test
    void operadorYAdmin_modifican() {
        dejoPasar(client.post().uri("/api/partners").cookie("access_token", OPERADOR).exchange());
        dejoPasar(client.delete().uri("/api/partners/x").cookie("access_token", OPERADOR).exchange());
        dejoPasar(client.put().uri("/api/partners/x").cookie("access_token", ADMIN).exchange());
    }

    @Test
    void smtpConfig_soloAdmin() {
        client.get().uri("/api/smtp-config").cookie("access_token", OPERADOR).exchange().expectStatus().isForbidden();
        client.delete().uri("/api/smtp-config/abc").cookie("access_token", OPERADOR).exchange().expectStatus().isForbidden();
        dejoPasar(client.get().uri("/api/smtp-config").cookie("access_token", ADMIN).exchange());
        client.get().uri("/api/smtp-config").exchange().expectStatus().isUnauthorized();
    }

    /**
     * Las reglas de rol se escriben sobre la ruta limpia: una ruta sin normalizar ({@code //api},
     * {@code ..}) no debe esquivarlas. El cortafuegos de Spring Security la rechaza con 400.
     */
    @Test
    void rutaSinNormalizar_rechazada() throws Exception {
        // HttpClient y no WebTestClient: este ultimo interpreta "//api/..." como un host.
        assertThat(crudo("DELETE", "//api/partners/x", AUDITOR)).isEqualTo(400);
        assertThat(crudo("POST", "//api/smtp-config", OPERADOR)).isEqualTo(400);
        assertThat(crudo("PUT", "/api//partners/x", AUDITOR)).isEqualTo(400);
        assertThat(crudo("POST", "/api/partners/../smtp-config", OPERADOR)).isEqualTo(400);
        assertThat(crudo("POST", "/api/partners/%2e%2e/smtp-config", OPERADOR)).isEqualTo(400);
    }

    private int crudo(String metodo, String rawPath, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .header("Cookie", "access_token=" + token)
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
