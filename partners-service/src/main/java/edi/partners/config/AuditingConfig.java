package edi.partners.config;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Rellena created_by / updated_by con el usuario de la peticion (o "system"). Se puede apagar
 * ({@code partners.jpa-auditing=false}) en pruebas que arrancan el servicio sin base de datos.
 */
@Configuration
@EnableJpaAuditing
@ConditionalOnProperty(name = "partners.jpa-auditing", havingValue = "true", matchIfMissing = true)
public class AuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(RequestIdentity.currentUser());
    }
}
