package edi.alerts.smtp;

/** Se pidio un correo de prueba sin configuracion guardada. */
public class SmtpNotConfiguredException extends RuntimeException {

    public SmtpNotConfiguredException() {
        super("Primero guarde la configuracion del servidor de correo");
    }
}
