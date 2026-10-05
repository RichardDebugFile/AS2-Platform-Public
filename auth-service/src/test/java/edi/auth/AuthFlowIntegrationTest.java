package edi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import edi.auth.repo.RefreshTokenRepository;
import edi.auth.svc.TotpService;
import edi.auth.testsupport.EnabledIfPostgres;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Escenarios de aceptacion de HU-02, HU-03 y HU-04 contra PostgreSQL real (migraciones Flyway,
 * seguridad completa y cookies). Cada ejecucion usa usuarios nuevos: la base local no se limpia.
 */
@EnabledIfPostgres
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "password1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TotpService totp;

    /** access_token y refresh_token tal como los dejo el servidor. */
    private record Session(Cookie access, Cookie refresh) {}

    private static Cookie cookie(MvcResult r, String name) {
        Pattern p = Pattern.compile("^" + name + "=([^;]*)");
        for (String h : r.getResponse().getHeaders("Set-Cookie")) {
            Matcher m = p.matcher(h);
            if (m.find()) {
                return new Cookie(name, m.group(1));
            }
        }
        throw new AssertionError("Sin cookie " + name);
    }

    private Session login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return new Session(cookie(r, "access_token"), cookie(r, "refresh_token"));
    }

    private record LoginBody(String username, String password) {}

    private Session admin() throws Exception {
        return login("admin", "admin123");
    }

    private String createUser(Session admin, String username, String role) throws Exception {
        String body = """
                {"username":"%s","password":"%s","email":"%s@example.com","roles":["%s"]}
                """.formatted(username, PASSWORD, username, role);
        MvcResult r = mvc.perform(post("/users").cookie(admin.access()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    /** Como la consola: las peticiones que modifican llevan el token CSRF. */
    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }

    private static MockHttpServletRequestBuilder patch(String url) {
        return MockMvcRequestBuilders.patch(url).with(csrf());
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void health_publico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void login_cookiesHttpOnly_yElRefreshSeGuardaComoHash() throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ADMIN\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("access_token="))))
                .andReturn();
        String refresh = cookie(r, "refresh_token").getValue();

        Integer enClaro = jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Integer.class,
                refresh);
        assertThat(enClaro).isZero();
        assertThat(refreshTokens.count()).isPositive();
    }

    @Test
    void csrf_conCookiesSinTokenSeRechaza_conBearerNoHaceFalta() throws Exception {
        Session s = admin();
        String body = "{\"username\":\"%s\",\"password\":\"password1\",\"email\":\"x@example.com\"}";

        // Una peticion forjada desde otro sitio llevaria las cookies, pero no la cabecera X-XSRF-TOKEN
        mvc.perform(MockMvcRequestBuilders.post("/auth/refresh").cookie(s.refresh()))
                .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/users").cookie(s.access()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(unique("csrf"))))
                .andExpect(status().isForbidden());
        // Authorization no la adjunta el navegador por su cuenta: no es forjable
        mvc.perform(MockMvcRequestBuilders.post("/users").header("Authorization", "Bearer " + s.access().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(unique("bearer"))))
                .andExpect(status().isCreated());
        // (La entrega de la cookie XSRF-TOKEN en cualquier GET se comprueba contra el JAR: el csrf() de
        // spring-security-test sustituye el repositorio de tokens y ya no escribe la cookie.)
    }

    @Test
    void credencialesInvalidas_mensajeGenerico() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-existe\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña inválidos"));
    }

    @Test
    void refresh_rota_yReutilizarElAnteriorDaTokenInvalid() throws Exception {
        Session s = admin();

        MvcResult rotated = mvc.perform(post("/auth/refresh").cookie(s.refresh()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(cookie(rotated, "refresh_token").getValue()).isNotEqualTo(s.refresh().getValue());

        mvc.perform(post("/auth/refresh").cookie(s.refresh()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(startsWith("refresh_token=;"), containsString("Max-Age=0")))));
    }

    @Test
    void jwks_validaLaFirmaDelAccessToken() throws Exception {
        Session s = admin();
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);

        SignedJWT token = SignedJWT.parse(s.access().getValue());

        assertThat(key.isPrivate()).isFalse();
        assertThat(token.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(token.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
    }

    @Test
    void tokenAlterado_esRechazado() throws Exception {
        Session s = admin();
        String forged = s.access().getValue().substring(0, s.access().getValue().length() - 4) + "AAAA";

        mvc.perform(get("/users/me").cookie(new Cookie("access_token", forged))).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me").cookie(s.access())).andExpect(status().isOk());
    }

    @Test
    void logout_revocaYBorraCookies() throws Exception {
        Session s = admin();

        mvc.perform(post("/auth/logout").cookie(s.refresh()))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(startsWith("access_token=;"), containsString("Max-Age=0")))));
        mvc.perform(post("/auth/refresh").cookie(s.refresh())).andExpect(status().isUnauthorized());
    }

    @Test
    void operador_noAdministraUsuarios_yAuditorRecibeSuRol() throws Exception {
        Session admin = admin();
        String operador = unique("op");
        String auditor = unique("aud");
        createUser(admin, operador, "OPERATOR");
        createUser(admin, auditor, "AUDITOR");

        Session op = login(operador, PASSWORD);
        mvc.perform(post("/users").cookie(op.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x" + operador + "\",\"password\":\"password1\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/users").cookie(op.access())).andExpect(status().isForbidden());
        mvc.perform(post("/auth/register").cookie(op.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"y" + operador + "\",\"password\":\"password1\",\"email\":\"y@example.com\"}"))
                .andExpect(status().isForbidden());

        SignedJWT token = SignedJWT.parse(login(auditor, PASSWORD).access().getValue());
        assertThat(token.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("AUDITOR");
    }

    @Test
    void usuarioDuplicadoSinDistinguirMayusculas_409() throws Exception {
        Session admin = admin();
        String nombre = unique("dup");
        createUser(admin, nombre, "OPERATOR");

        mvc.perform(post("/users").cookie(admin.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + nombre.toUpperCase() + "\",\"password\":\"password1\",\"email\":\"d@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void desactivar_cortaLaRenovacion() throws Exception {
        Session admin = admin();
        String nombre = unique("off");
        String id = createUser(admin, nombre, "OPERATOR");
        Session s = login(nombre, PASSWORD);

        mvc.perform(patch("/users/" + id + "/active").cookie(admin.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post("/auth/refresh").cookie(s.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + nombre + "\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarRoles_revocaSesiones_yListadoPaginado() throws Exception {
        Session admin = admin();
        String nombre = unique("rol");
        String id = createUser(admin, nombre, "OPERATOR");
        Session s = login(nombre, PASSWORD);

        mvc.perform(patch("/users/" + id + "/roles").cookie(admin.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"AUDITOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));
        mvc.perform(post("/auth/refresh").cookie(s.refresh())).andExpect(status().isUnauthorized());

        mvc.perform(get("/users").param("q", nombre).cookie(admin.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(nombre))
                // Forma fija que consume la consola (usersStore)
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(patch("/users/" + UUID.randomUUID() + "/active").cookie(admin.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mfa_altaYLoginConSegundoFactor() throws Exception {
        Session admin = admin();
        String nombre = unique("mfa");
        createUser(admin, nombre, "OPERATOR");
        Session s = login(nombre, PASSWORD);

        // Alta: setup devuelve el QR/URI y la verificacion activa
        JsonNode setup = json.readTree(mvc.perform(post("/users/me/mfa/setup").cookie(s.access()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Matcher m = Pattern.compile("secret=([A-Z2-7]+)").matcher(setup.get("otpauthUrl").asString());
        assertThat(m.find()).isTrue();
        String secret = m.group(1);
        String guardado = jdbc.queryForObject("select mfa_secret from user_account where lower(username) = lower(?)",
                String.class, nombre);
        assertThat(guardado).isNotEqualTo(secret).doesNotContain(secret);

        mvc.perform(post("/users/me/mfa/verify").cookie(s.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + totp.codeAt(secret, Instant.now()) + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/users/me/mfa/status").cookie(s.access())).andExpect(jsonPath("$.enabled").value(true));

        // Login: la contrasena ya no basta
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + nombre + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String mfaToken = json.readTree(r.getResponse().getContentAsString()).get("mfaToken").asString();

        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
        String ok = "{\"mfaToken\":\"" + mfaToken + "\",\"code\":\"" + totp.codeAt(secret, Instant.now()) + "\"}";
        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(startsWith("access_token="))));
        // Desafio de un solo uso
        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_EXPIRED"));
    }
}
