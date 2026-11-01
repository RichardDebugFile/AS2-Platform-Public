package edi.documents.store;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPayloadRepository extends JpaRepository<DocumentPayload, UUID> {
}
