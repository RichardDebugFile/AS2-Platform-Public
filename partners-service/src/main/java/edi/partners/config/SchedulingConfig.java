package edi.partners.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tareas programadas del servicio (retiro diario de certificados caducados, HU-11). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
