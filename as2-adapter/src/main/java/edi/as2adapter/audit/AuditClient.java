package edi.as2adapter.audit;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Registra los eventos del intercambio AS2 en la bitacora de partners-service sin bloquear ni
 * interrumpir el flujo: el envio se hace en segundo plano y, si la bitacora no responde, el evento
 * queda en el log de este servicio (logger {@code audit}) y el intercambio sigue.
 */
@Component
public class AuditClient {

    static final String SOURCE = "as2-adapter";
    static final String PATH = "/internal/audit-logs";
    private static final Logger log = LoggerFactory.getLogger("audit");

    private final RestClient http;
    private final Executor executor;

    @Autowired
    public AuditClient(@Value("${as2.audit.url}") String baseUrl) {
        this(RestClient.builder().requestFactory(timeouts()), baseUrl, Executors.newVirtualThreadPerTaskExecutor());
    }

    AuditClient(RestClient.Builder builder, String baseUrl, Executor executor) {
        this.http = builder.baseUrl(baseUrl).build();
        this.executor = executor;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(5));
        return f;
    }

    /** Vuelve de inmediato y nunca lanza: la bitacora no puede detener un envio o una recepcion. */
    public void publish(AuditEvent event) {
        try {
            executor.execute(() -> send(event));
        } catch (RuntimeException e) {
            failed(event, e);
        }
    }

    private void send(AuditEvent event) {
        try {
            http.post().uri(PATH).contentType(MediaType.APPLICATION_JSON).body(event.body()).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            failed(event, e);
        }
    }

    private static void failed(AuditEvent e, Exception cause) {
        if (!log.isWarnEnabled()) {
            return;
        }
        log.warn("No se pudo registrar en la bitacora: event={} status={} partner={} messageId={} causa={}",
                e.eventType(), e.status(), e.partnerAs2Name(), e.messageId(), cause.toString());
    }
}
