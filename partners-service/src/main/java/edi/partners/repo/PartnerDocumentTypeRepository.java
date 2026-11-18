package edi.partners.repo;

import edi.partners.domain.PartnerDocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PartnerDocumentTypeRepository extends JpaRepository<PartnerDocumentType, UUID> {

    @EntityGraph(attributePaths = {"documentType", "documentType.responses"})
    @Query("select a from PartnerDocumentType a where a.partner.id = :partnerId"
            + " order by a.documentType.code, a.direction")
    List<PartnerDocumentType> findByPartner(UUID partnerId);

    /** Identificadores AS2 de los socios que usan el tipo, sin repetir. */
    @Query("select distinct a.partner.as2Name from PartnerDocumentType a where a.documentType.id = :typeId"
            + " order by a.partner.as2Name")
    List<String> partnersUsing(UUID typeId);

    /** Cuantos socios usan cada tipo: [id del tipo, cantidad]. */
    @Query("select a.documentType.id, count(distinct a.partner.id) from PartnerDocumentType a"
            + " group by a.documentType.id")
    List<Object[]> usageCounts();

    void deleteByPartnerId(UUID partnerId);
}
