package edi.gateway.alerts.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Cliente HTTP hacia alerts-service con tiempos de espera cortos: el envio de eventos nunca debe
 * retrasar la respuesta al usuario.
 */
@Configuration
@EnableConfigurationProperties(AlertsClientProperties.class)
public class AlertsIngestConfig {

    @Bean
    WebClient alertsWebClient(AlertsClientProperties props) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(props.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .baseUrl(props.getBaseUrl() == null ? "" : props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Alerts-Token", props.getToken() == null ? "" : props.getToken())
                .build();
    }
}
