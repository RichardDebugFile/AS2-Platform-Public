package edi.partners.repo;

import edi.partners.domain.MdnConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MdnConfigRepository extends JpaRepository<MdnConfig, UUID> {

    Optional<MdnConfig> findByPartnerId(UUID partnerId);
}
