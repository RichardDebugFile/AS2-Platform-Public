package edi.alerts.smtp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Configuracion del servidor de correo: una sola fila (AS2-106). */
@Entity
@Table(name = "smtp_config")
public class SmtpConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SmtpSecurity security;

    @Column(length = 255)
    private String username;

    /** Contrasena cifrada ({@code gcm:...}); nunca sale del servicio. */
    @Column(name = "password_enc", length = 512)
    private String passwordEnc;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    @Column(name = "from_name", length = 120)
    private String fromName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    protected SmtpConfig() {
    }

    static SmtpConfig create() {
        SmtpConfig c = new SmtpConfig();
        c.id = SINGLETON_ID;
        return c;
    }

    public String getHost() {
        return host;
    }

    void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    void setPort(int port) {
        this.port = port;
    }

    public SmtpSecurity getSecurity() {
        return security;
    }

    void setSecurity(SmtpSecurity security) {
        this.security = security;
    }

    public String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getPasswordEnc() {
        return passwordEnc;
    }

    void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    public boolean hasPassword() {
        return passwordEnc != null && !passwordEnc.isBlank();
    }

    public String getFromAddress() {
        return fromAddress;
    }

    void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    void touched(Instant at, String by) {
        this.updatedAt = at;
        this.updatedBy = by;
    }
}
