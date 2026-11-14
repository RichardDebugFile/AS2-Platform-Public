package edi.alerts.smtp;

/** El servidor de correo no acepto el mensaje; el texto explica la causa para la consola. */
public class SmtpSendException extends RuntimeException {

    public SmtpSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
