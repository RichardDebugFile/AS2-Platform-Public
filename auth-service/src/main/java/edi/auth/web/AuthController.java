package edi.auth.web;

import edi.auth.dto.CreateUserRequest;
import edi.auth.dto.LoginRequest;
import edi.auth.dto.LoginResponse;
import edi.auth.dto.MfaRequiredResponse;
import edi.auth.dto.MfaVerifyRequest;
import edi.auth.dto.RefreshRequest;
import edi.auth.dto.RegisterRequest;
import edi.auth.dto.UserResponse;
import edi.auth.error.AuthException;
import edi.auth.svc.AuthService;
import edi.auth.svc.AuthService.IssuedSession;
import edi.auth.svc.AuthService.LoginOutcome;
import edi.auth.svc.SessionPolicy;
import edi.auth.svc.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login, segundo factor, renovacion y cierre de sesion. Los tokens viajan solo en cookies (RN-02). */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final UserService users;
    private final SessionPolicy policy;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, UserService users, SessionPolicy policy, AuthCookies cookies) {
        this.auth = auth;
        this.users = users;
        this.policy = policy;
        this.cookies = cookies;
    }

    /**
     * Con MFA activo la contrasena correcta no abre sesion: responde {@code MFA_REQUIRED} y un
     * {@code mfaToken} para {@code /auth/mfa/verify}, sin cookies.
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody @Valid LoginRequest req,
                                        HttpServletRequest request, HttpServletResponse response) {
        LoginOutcome outcome = auth.login(req.username(), req.password());
        if (outcome.mfaRequired()) {
            return ResponseEntity.ok(MfaRequiredResponse.of(outcome.mfaToken()));
        }
        return ResponseEntity.ok(send(outcome.session(), request, response));
    }

    @PostMapping("/mfa/verify")
    public LoginResponse mfaVerify(@RequestBody @Valid MfaVerifyRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        return send(auth.verifyMfa(req.mfaToken(), req.code()), request, response);
    }

    /** Lee el refresh de la cookie (o del cuerpo, para clientes sin cookies) y rota el par. */
    @PostMapping("/refresh")
    public LoginResponse refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String cookieToken,
                                 @RequestBody(required = false) RefreshRequest body,
                                 HttpServletRequest request, HttpServletResponse response) {
        try {
            return send(auth.refresh(pick(cookieToken, body)), request, response);
        } catch (AuthException e) {
            // Cookies fuera ante cualquier rechazo: si se quedaran, el navegador seguiria enviando un
            // refresh inutil (y un access que puede seguir vivo unos minutos) en cada peticion.
            cookies.clear(request, response);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String cookieToken,
                                       @RequestBody(required = false) RefreshRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        auth.logout(pick(cookieToken, body));
        cookies.clear(request, response);
        return ResponseEntity.noContent().build();
    }

    /** Alta de usuario OPERATOR. Exige ADMIN (SecurityConfig): no hay auto-registro publico. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody @Valid RegisterRequest req) {
        return users.create(new CreateUserRequest(req.username(), req.password(), req.email(), null, true));
    }

    private LoginResponse send(IssuedSession s, HttpServletRequest request, HttpServletResponse response) {
        cookies.write(request, response, s.accessToken(), policy.accessTtlSeconds(), s.refreshToken(),
                s.refreshExpiresAt());
        return s.body();
    }

    private static String pick(String cookieToken, RefreshRequest body) {
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        return body == null ? null : body.refreshToken();
    }
}
