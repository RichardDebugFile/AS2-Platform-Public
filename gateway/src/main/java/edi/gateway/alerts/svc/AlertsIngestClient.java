package edi.gateway.alerts.svc;

import edi.gateway.alerts.config.AlertsClientProperties;
import edi.gateway.alerts.dto.IngestApiCallRequest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Envia eventos de API a alerts-service. Los errores se ignoran: no afectan a la peticion. */
@Component
public class AlertsIngestClient {

    private static final Logger log = LoggerFactory.getLogger(AlertsIngestClient.class);

    private final WebClient alertsWebClient;
    private final AlertsClientProperties props;

    public AlertsIngestClient(WebClient alertsWebClient, AlertsClientProperties props) {
        this.alertsWebClient = alertsWebClient;
        this.props = props;
    }

    public Mono<Void> send(IngestApiCallRequest payload) {
        if (!props.isConfigured()) {
            return Mono.empty();
        }
        return alertsWebClient
                .post()
                .uri(props.getIngestPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .doOnError(ex -> log.debug("[alerts] fallo enviando evento: {}", ex.toString()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }
}
