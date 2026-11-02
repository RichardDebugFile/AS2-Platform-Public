package edi.documents.store;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentMdnRepository extends JpaRepository<DocumentMdn, UUID> {
}
