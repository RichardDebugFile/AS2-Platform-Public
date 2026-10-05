package edi.auth.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edi.auth.domain.Role;
import edi.auth.dto.LoginResponse;
import edi.auth.dto.UserResponse;
import edi.auth.error.AuthException;
import edi.auth.svc.AuthService;
import edi.auth.svc.AuthService.IssuedSession;
import edi.auth.svc.AuthService.LoginOutcome;
import edi.auth.svc.SessionPolicy;
import edi.auth.svc.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Capa HTTP: cookies, cuerpos y codigos de error. La logica se prueba en AuthServiceTest. */
class AuthControllerTest {

    private static final String LOGIN = "{\"username\":\"alice\",\"password\":\"password1\"}";

    private AuthService auth;
    private UserService users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        users = mock(UserService.class);
        AuthController controller = new AuthController(auth, users, new SessionPolicy(5, 15, 60), new AuthCookies());
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static IssuedSession session() {
        Instant refreshExp = Instant.now().plusSeconds(1200);
        LoginResponse body = new LoginResponse("alice", "alice@example.com", Set.of("ADMIN"), 300,
                Instant.now().plusSeconds(3600).toEpochMilli(), refreshExp.toEpochMilli(), 900, 3600);
        return new IssuedSession("ACCESS.JWT", "REFRESH.JWT", refreshExp, body);
    }

    @Test
    void login_ok_cookiesHttpOnlyYSinTokensEnElCuerpo() throws Exception {
        when(auth.login("alice", "password1")).thenReturn(new LoginOutcome(session(), null));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(content().string(not(containsString("ACCESS.JWT"))))
                .andExpect(content().string(not(containsString("REFRESH.JWT"))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("access_token=ACCESS.JWT"), containsString("Max-Age=300"),
                        containsString("HttpOnly"), containsString("SameSite=Lax")))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refresh_token=REFRESH.JWT"))))
                .andExpect(header().stringValues("Set-Cookie", everyItem(not(containsString("Secure")))));
    }

    @Test
    void login_porHttps_cookiesSecure() throws Exception {
        when(auth.login("alice", "password1")).thenReturn(new LoginOutcome(session(), null));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN)
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(header().stringValues("Set-Cookie", everyItem(containsString("Secure"))));
    }

    @Test
    void login_cookieRefreshSobreviveAlTokenElMargen() throws Exception {
        when(auth.login("alice", "password1")).thenReturn(new LoginOutcome(session(), null));

        // ventana de 1200 s + margen de 300 s (un segundo menos si el reloj avanza durante el test)
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(
                        containsString("refresh_token="),
                        anyOf(containsString("Max-Age=1500"), containsString("Max-Age=1499"))))));
    }

    @Test
    void login_conMfa_respondeDesafioSinCookies() throws Exception {
        when(auth.login("alice", "password1")).thenReturn(new LoginOutcome(null, "CH-1"));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.mfaToken").value("CH-1"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void login_invalido_401ConCodigo() throws Exception {
        when(auth.login(any(), any())).thenThrow(AuthException.badCredentials());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Usuario o contraseña inválidos"));
    }

    @Test
    void login_cuerpoInvalido_400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("no-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mfaVerify_ok_ponCookies() throws Exception {
        when(auth.verifyMfa("CH-1", "123456")).thenReturn(session());

        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"CH-1\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("access_token=ACCESS.JWT"))));
    }

    @Test
    void mfaVerify_codigoMalFormado_400() throws Exception {
        mvc.perform(post("/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"CH-1\",\"code\":\"12ab56\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_conCookie_rota() throws Exception {
        when(auth.refresh("OLD")).thenReturn(session());

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "OLD")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Set-Cookie", hasItem(containsString("refresh_token=REFRESH.JWT"))));
    }

    @Test
    void refresh_conCuerpo_paraClientesSinCookies() throws Exception {
        when(auth.refresh("OLD")).thenReturn(session());

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"OLD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_rechazado_borraCookiesYExplicaElMotivo() throws Exception {
        when(auth.refresh("OLD")).thenThrow(new AuthException(AuthException.IDLE_EXPIRED, "inactividad"));

        mvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "OLD")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDLE_EXPIRED"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(startsWith("access_token=;"), containsString("Max-Age=0")))))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(startsWith("refresh_token=;"), containsString("Max-Age=0")))));
    }

    @Test
    void logout_revocaYBorraAmbasCookies() throws Exception {
        mvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", "RT")))
                .andExpect(status().isNoContent())
                .andExpect(header().stringValues("Set-Cookie", everyItem(containsString("Max-Age=0"))));
        verify(auth).logout("RT");
    }

    @Test
    void register_creaOperador() throws Exception {
        when(users.create(any())).thenReturn(
                new UserResponse(UUID.randomUUID(), "bob", "bob@example.com", Set.of(Role.OPERATOR), true, false));

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"password1\",\"email\":\"bob@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
    }
}
