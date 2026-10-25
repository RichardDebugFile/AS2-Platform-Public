package edi.partners.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Rellena created_by / updated_by con el usuario de la peticion (o "system"). */
@Configuration
@EnableJpaAuditing
public class AuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(RequestIdentity.currentUser());
    }
}
