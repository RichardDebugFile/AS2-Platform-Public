package edi.partners.repo;

import edi.partners.domain.IncomingConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomingConfigRepository extends JpaRepository<IncomingConfig, UUID> {

    Optional<IncomingConfig> findByPartnerId(UUID partnerId);
}
