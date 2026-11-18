package edi.alerts.smtp;

import java.time.Instant;

/** La configuracion como la ve la consola: sin la contrasena, solo si hay una guardada. */
public record SmtpConfigView(boolean configured, String host, Integer port, SmtpSecurity security, String username,
        boolean passwordSet, String fromAddress, String fromName, Instant updatedAt, String updatedBy) {

    static SmtpConfigView none() {
        return new SmtpConfigView(false, null, null, null, null, false, null, null, null, null);
    }

    static SmtpConfigView of(SmtpConfig c) {
        return new SmtpConfigView(true, c.getHost(), c.getPort(), c.getSecurity(), c.getUsername(), c.hasPassword(),
                c.getFromAddress(), c.getFromName(), c.getUpdatedAt(), c.getUpdatedBy());
    }
}
