package edi.alerts.smtp;

import edi.alerts.audit.AuditClient;
import edi.alerts.audit.AuditEvent;
import edi.alerts.audit.AuditEvent.Change;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuracion del servidor de correo y correo de prueba (AS2-106, adelanto de HU-21). La contrasena
 * se guarda cifrada y nunca se devuelve; cada cambio y cada prueba quedan en la bitacora.
 */
@Service
public class SmtpConfigService {

    static final String CONFIG_UPDATED = "SMTP_CONFIG_UPDATED";
    static final String TEST_SENT = "SMTP_TEST_SENT";
    static final String SUBJECT = "Prueba de correo de la Plataforma AS2/EDI";
    private static final String CHANGED = "(cambiada)";
    private static final String REMOVED = "(eliminada)";
    private static final String DEFAULT_FROM_NAME = "Plataforma AS2/EDI";

    private final SmtpConfigRepository repo;
    private final SecretCipher cipher;
    private final MailSenderFactory senders;
    private final AuditClient audit;
    private final Clock clock;
    private final DateTimeFormatter when;

    public SmtpConfigService(SmtpConfigRepository repo, SecretCipher cipher, MailSenderFactory senders,
            AuditClient audit, Clock clock, @Value("${alerts.zone:America/Guayaquil}") String zone) {
        this.repo = repo;
        this.cipher = cipher;
        this.senders = senders;
        this.audit = audit;
        this.clock = clock;
        this.when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT).withZone(ZoneId.of(zone));
    }

    @Transactional(readOnly = true)
    public SmtpConfigView view() {
        return repo.findById(SmtpConfig.SINGLETON_ID).map(SmtpConfigView::of).orElseGet(SmtpConfigView::none);
    }

    @Transactional
    public SmtpConfigView save(SmtpConfigRequest req, String actor) {
        SmtpConfig current = repo.findById(SmtpConfig.SINGLETON_ID).orElse(null);
        SmtpConfig c = current != null ? current : SmtpConfig.create();
        Map<String, Change> changes = new LinkedHashMap<>();

        String username = blankToNull(req.username());
        String fromName = blankToNull(req.fromName());
        track(changes, "host", c.getHost(), req.host().strip());
        track(changes, "port", current == null ? null : c.getPort(), req.port());
        track(changes, "security", c.getSecurity(), req.security());
        track(changes, "username", c.getUsername(), username);
        track(changes, "fromAddress", c.getFromAddress(), req.fromAddress().strip());
        track(changes, "fromName", c.getFromName(), fromName);

        String newPassword = req.password() == null || req.password().isEmpty() ? null : req.password();
        if (username == null || req.clearPassword()) {
            if (c.hasPassword()) {
                changes.put("password", new Change(CHANGED, REMOVED));
            }
            c.setPasswordEnc(null);
        } else if (newPassword != null) {
            changes.put("password", new Change(c.hasPassword() ? CHANGED : null, CHANGED));
            c.setPasswordEnc(cipher.encrypt(newPassword));
        }
        if (username != null && !c.hasPassword()) {
            throw new IllegalArgumentException("Con usuario SMTP hace falta la contrasena");
        }

        c.setHost(req.host().strip());
        c.setPort(req.port());
        c.setSecurity(req.security());
        c.setUsername(username);
        c.setFromAddress(req.fromAddress().strip());
        c.setFromName(fromName);
        c.touched(Instant.now(clock), actor);
        SmtpConfig saved = repo.save(c);

        audit.publish(new AuditEvent(CONFIG_UPDATED, true, actor, "Servidor de correo guardado: " + saved.getHost()
                + ":" + saved.getPort() + " (" + saved.getSecurity() + "), remitente " + saved.getFromAddress(),
                changes));
        return SmtpConfigView.of(saved);
    }

    /** Envia el correo de prueba con la configuracion guardada; si el servidor no lo acepta, explica por que. */
    @Transactional(readOnly = true)
    public TestEmailResult sendTest(String to, String actor) {
        SmtpConfig c = repo.findById(SmtpConfig.SINGLETON_ID).orElseThrow(SmtpNotConfiguredException::new);
        String recipient = to.strip();
        SmtpSettings s;
        try {
            s = settings(c);
        } catch (IllegalStateException e) {
            throw failed(recipient, actor, c.getHost() + ":" + c.getPort(), e.getMessage(), e);
        }
        long start = System.nanoTime();
        try {
            JavaMailSender sender = senders.create(s);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(message, false, "UTF-8");
            h.setFrom(s.fromAddress(), s.fromName() != null ? s.fromName() : DEFAULT_FROM_NAME);
            h.setTo(recipient);
            h.setSubject(SUBJECT);
            h.setText(body(actor), false);
            sender.send(message);
        } catch (MailAuthenticationException e) {
            throw failed(recipient, actor, s.server(), "el servidor rechazo el usuario o la contrasena", e);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            throw failed(recipient, actor, s.server(), cause(e), e);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        audit.publish(new AuditEvent(TEST_SENT, true, actor,
                "Correo de prueba enviado a " + recipient + " por " + s.server(), Map.of()));
        return new TestEmailResult(true, recipient, s.server(), elapsed);
    }

    private SmtpSettings settings(SmtpConfig c) {
        String password = c.hasPassword() ? cipher.decrypt(c.getPasswordEnc()) : null;
        return new SmtpSettings(c.getHost(), c.getPort(), c.getSecurity(), c.getUsername(), password,
                c.getFromAddress(), c.getFromName());
    }

    private SmtpSendException failed(String to, String actor, String server, String why, Exception e) {
        String message = "No se pudo enviar el correo de prueba por " + server + ": " + why;
        audit.publish(new AuditEvent(TEST_SENT, false, actor, "Correo de prueba a " + to + ": " + why, Map.of()));
        return new SmtpSendException(message, e);
    }

    private String body(String actor) {
        return "Este es un correo de prueba de la Plataforma AS2/EDI, enviado por " + actor + " el "
                + when.format(Instant.now(clock)) + ".\n\n"
                + "Si lo recibe, la configuracion del servidor de correo funciona.\n";
    }

    /** La causa mas concreta que da JavaMail (p. ej. "Connection refused" o el codigo SMTP). */
    static String cause(Throwable e) {
        Throwable t = e;
        String last = e.getMessage();
        while (t != null) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                last = t.getMessage();
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        return last == null || last.isBlank() ? e.getClass().getSimpleName() : last.strip();
    }

    private static void track(Map<String, Change> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            changes.put(field, new Change(from, to));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
