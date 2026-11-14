package edi.alerts.smtp;

/** Como se protege la conexion con el servidor de correo. */
public enum SmtpSecurity {
    /** Sin cifrado (solo para servidores de prueba en la red interna, p. ej. Mailpit). */
    NONE,
    /** Conexion en claro que pasa a TLS con STARTTLS (puerto 587 habitual). */
    STARTTLS,
    /** TLS desde el inicio (SMTPS, puerto 465 habitual). */
    SSL
}
