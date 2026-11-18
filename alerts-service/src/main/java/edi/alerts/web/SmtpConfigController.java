package edi.alerts.web;

import edi.alerts.smtp.SmtpConfigRequest;
import edi.alerts.smtp.SmtpConfigService;
import edi.alerts.smtp.SmtpConfigView;
import edi.alerts.smtp.TestEmailRequest;
import edi.alerts.smtp.TestEmailResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuracion del servidor de correo (AS2-106). El gateway solo deja pasar a ADMIN y reenvia el
 * usuario de la sesion en {@code X-User-Name}; alerts-service no valida tokens.
 */
@RestController
@RequestMapping("/api/smtp-config")
public class SmtpConfigController {

    public static final String USER_HEADER = "X-User-Name";
    static final String SYSTEM = "system";
    private static final int MAX_ACTOR = 64;

    private final SmtpConfigService service;

    public SmtpConfigController(SmtpConfigService service) {
        this.service = service;
    }

    @GetMapping
    public SmtpConfigView get() {
        return service.view();
    }

    @PutMapping
    public SmtpConfigView save(@Valid @RequestBody SmtpConfigRequest request,
            @RequestHeader(name = USER_HEADER, required = false) String user) {
        return service.save(request, actor(user));
    }

    @PostMapping("/test")
    public TestEmailResult test(@Valid @RequestBody TestEmailRequest request,
            @RequestHeader(name = USER_HEADER, required = false) String user) {
        return service.sendTest(request.to(), actor(user));
    }

    /** Usuario para la bitacora: sin cabecera (llamada interna) queda "system"; largo maximo de la columna. */
    static String actor(String header) {
        String user = header == null ? "" : header.strip();
        if (user.isEmpty()) {
            return SYSTEM;
        }
        return user.length() > MAX_ACTOR ? user.substring(0, MAX_ACTOR) : user;
    }
}
