package edi.alerts.audit;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Registra los cambios de la configuracion de correo en la bitacora de partners-service
 * ({@code POST /internal/audit-logs}). Cada evento sale en un hilo virtual: guardar la configuracion
 * no espera a la bitacora, y si esta no responde el evento queda en el log (logger {@code audit}).
 */
@Component
public class AuditClient {

    static final String SOURCE = "alerts-service";
    private static final Logger log = LoggerFactory.getLogger("audit");

    private final RestClient http;

    public AuditClient(@Value("${alerts.audit.url:http://localhost:8081}") String baseUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    /** Vuelve de inmediato y nunca lanza. */
    public void publish(AuditEvent event) {
        Thread.startVirtualThread(() -> {
            try {
                http.post().uri("/internal/audit-logs").contentType(MediaType.APPLICATION_JSON)
                        .body(event.body()).retrieve().toBodilessEntity();
            } catch (RuntimeException e) {
                log.warn("Bitacora no disponible, evento sin registrar: {} de {} ({})", event.eventType(),
                        event.actor(), e.toString());
            }
        });
    }
}
