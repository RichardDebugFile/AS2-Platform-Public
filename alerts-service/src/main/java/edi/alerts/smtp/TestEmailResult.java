package edi.alerts.smtp;

/** Correo de prueba entregado al servidor SMTP. */
public record TestEmailResult(boolean sent, String to, String server, long elapsedMs) {
}
