package edi.partners.repo;

import edi.partners.domain.Partner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    List<Partner> findAllByActiveOrderByAs2NameAsc(boolean active);

    Optional<Partner> findByAs2NameIgnoreCaseAndActiveTrue(String as2Name);

    boolean existsByAs2NameIgnoreCaseAndActiveTrue(String as2Name);

    boolean existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot(String as2Name, UUID id);
}
