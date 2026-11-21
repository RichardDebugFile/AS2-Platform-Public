package edi.partners.repo;

import edi.partners.domain.DocumentStandard;
import edi.partners.domain.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    Optional<DocumentType> findByStandardAndCodeAndVersion(DocumentStandard standard, String code, String version);

    @EntityGraph(attributePaths = "responses")
    @Query("select t from DocumentType t order by t.standard, t.code, t.version")
    List<DocumentType> findAllWithResponses();

    /** Tipos que esperan a este como respuesta: se le quita la correlacion antes de borrarlo. */
    @Query("select t from DocumentType t join t.responses r where r.id = :id")
    List<DocumentType> findExpecting(UUID id);
}
