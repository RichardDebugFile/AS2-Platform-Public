package edi.gateway.errors;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Resolucion DNS acotada hacia los servicios (AS2-102); el limite se ajusta con GATEWAY_DNS_TIMEOUT_MS. */
@Configuration
public class UpstreamDnsConfig {

    @Bean
    HttpClientCustomizer fastDnsFailure(@Value("${gateway.upstream.dns-timeout-ms:2000}") long timeoutMs) {
        return new FastDnsFailure(Duration.ofMillis(timeoutMs), null);
    }
}
