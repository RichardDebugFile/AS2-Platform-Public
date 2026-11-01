package edi.documents.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    /** Donde esta el archivo de cada documento, para el reporte de consistencia. */
    record FileRef(UUID id, String messageId, String partnerAs2Name, String filePath) {
    }

    Optional<Document> findByMessageId(String messageId);

    List<Document> findByPartnerCopyStatusAndCreatedAtAfterOrderByCreatedAtAsc(PartnerCopyStatus status,
            Instant since);

    @Query("select new edi.documents.store.DocumentRepository.FileRef(d.id, d.messageId, d.partnerAs2Name, d.filePath)"
            + " from Document d")
    List<FileRef> fileRefs();

    @Query("select d.status, count(d) from Document d group by d.status")
    List<Object[]> countByStatus();

    @Query("select d.typeCode, count(d) from Document d group by d.typeCode")
    List<Object[]> countByType();

    @Query("select d.direction, count(d) from Document d group by d.direction")
    List<Object[]> countByDirection();

    @Query("select d.createdAt from Document d where d.createdAt >= :since")
    List<Instant> createdSince(@Param("since") Instant since);
}
