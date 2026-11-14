package edi.alerts.web;

import edi.alerts.config.RequestIdentity;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuracion del servidor de correo (AS2-106). El gateway solo deja pasar a ADMIN. */
@RestController
@RequestMapping("/api/smtp-config")
public class SmtpConfigController {

    private final SmtpConfigService service;

    public SmtpConfigController(SmtpConfigService service) {
        this.service = service;
    }

    @GetMapping
    public SmtpConfigView get() {
        return service.view();
    }

    @PutMapping
    public SmtpConfigView save(@Valid @RequestBody SmtpConfigRequest request) {
        return service.save(request, RequestIdentity.currentUser());
    }

    @PostMapping("/test")
    public TestEmailResult test(@Valid @RequestBody TestEmailRequest request) {
        return service.sendTest(request.to(), RequestIdentity.currentUser());
    }
}
