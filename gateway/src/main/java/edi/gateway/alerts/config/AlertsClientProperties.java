package edi.gateway.alerts.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion del envio de eventos de API a alerts-service ({@code alerts.client.*}). */
@ConfigurationProperties(prefix = "alerts.client")
public class AlertsClientProperties {

    private boolean enabled = false;
    private String baseUrl;
    private String ingestPath = "/internal/ingest/api-call";
    private String token;
    private String serviceName = "gateway";
    private int connectTimeoutMs = 500;
    private int readTimeoutMs = 1000;
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/internal/**", "/api/alerts/**"));

    /** Solo envia si esta activado y tiene destino y token. */
    public boolean isConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getIngestPath() {
        return ingestPath;
    }

    public void setIngestPath(String ingestPath) {
        this.ingestPath = ingestPath;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }
}
