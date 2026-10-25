package edi.partners.repo;

import edi.partners.domain.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository
        extends JpaRepository<AuditLogEntry, UUID>, JpaSpecificationExecutor<AuditLogEntry> {

    @Query("select distinct a.eventType from AuditLogEntry a order by a.eventType")
    List<String> findEventTypes();
}
