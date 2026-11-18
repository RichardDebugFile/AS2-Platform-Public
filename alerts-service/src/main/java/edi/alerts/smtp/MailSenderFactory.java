package edi.alerts.smtp;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Arma el cliente SMTP con la configuracion guardada; se crea en cada envio porque puede cambiar. */
@Component
public class MailSenderFactory {

    private final String timeoutMs;

    public MailSenderFactory(@Value("${alerts.mail.timeout-ms:10000}") int timeoutMs) {
        this.timeoutMs = String.valueOf(timeoutMs);
    }

    JavaMailSender create(SmtpSettings s) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(s.host());
        sender.setPort(s.port());
        sender.setDefaultEncoding("UTF-8");
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.connectiontimeout", timeoutMs);
        p.put("mail.smtp.timeout", timeoutMs);
        p.put("mail.smtp.writetimeout", timeoutMs);
        if (s.authenticates()) {
            sender.setUsername(s.username());
            sender.setPassword(s.password());
            p.put("mail.smtp.auth", "true");
        }
        switch (s.security()) {
            case STARTTLS -> {
                p.put("mail.smtp.starttls.enable", "true");
                p.put("mail.smtp.starttls.required", "true");
            }
            case SSL -> p.put("mail.smtp.ssl.enable", "true");
            case NONE -> {
                // Sin cifrado: solo servidores de prueba de la red interna.
            }
        }
        return sender;
    }
}
