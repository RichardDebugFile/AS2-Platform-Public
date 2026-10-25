package edi.partners.repo;

import edi.partners.domain.OutgoingConfig;
import edi.partners.domain.SendingMode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutgoingConfigRepository extends JpaRepository<OutgoingConfig, UUID> {

    Optional<OutgoingConfig> findByPartnerId(UUID partnerId);

    boolean existsByPartnerIdAndSendingModeNot(UUID partnerId, SendingMode mode);
}
