package edi.alerts.smtp;

/** Configuracion lista para enviar, con la contrasena ya descifrada. Solo vive en memoria. */
record SmtpSettings(String host, int port, SmtpSecurity security, String username, String password,
        String fromAddress, String fromName) {

    boolean authenticates() {
        return username != null && !username.isBlank();
    }

    String server() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return "SmtpSettings[" + server() + ", " + security + ", usuario=" + username + "]";
    }
}
