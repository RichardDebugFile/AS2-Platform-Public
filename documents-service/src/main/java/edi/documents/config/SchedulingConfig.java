package edi.documents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas (recuperacion de copias en la carpeta del socio, HU-14). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
