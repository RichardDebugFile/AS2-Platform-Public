package edi.partners.repo;

import edi.partners.domain.KeyUsage;
import edi.partners.domain.PartnerCertificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartnerCertificateRepository extends JpaRepository<PartnerCertificate, UUID> {

    List<PartnerCertificate> findAllByPartnerId(UUID partnerId);

    List<PartnerCertificate> findAllByPartnerIdAndKeyUsage(UUID partnerId, KeyUsage keyUsage);

    List<PartnerCertificate> findAllByPartnerIdAndKeyUsageAndActiveTrue(UUID partnerId, KeyUsage keyUsage);

    Optional<PartnerCertificate> findByIdAndPartnerId(UUID id, UUID partnerId);

    Optional<PartnerCertificate> findByPartnerIdAndKeyUsageAndFingerprintSha256(UUID partnerId, KeyUsage keyUsage,
                                                                                String fingerprintSha256);

    /** Activos ya caducados: los candidatos del retiro diario. */
    @Query("select c from PartnerCertificate c join fetch c.partner where c.active = true and c.validTo <= :now")
    List<PartnerCertificate> findActiveExpired(Instant now);
}
