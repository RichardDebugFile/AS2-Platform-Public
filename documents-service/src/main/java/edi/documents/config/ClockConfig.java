package edi.documents.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la zona del negocio: fechas y horas de los documentos generados. Las pruebas lo reemplazan. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${documents.zone:America/Guayaquil}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
