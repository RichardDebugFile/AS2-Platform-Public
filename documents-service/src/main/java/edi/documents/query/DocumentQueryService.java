package edi.documents.query;

import edi.documents.ingest.DocumentNotFoundException;
import edi.documents.storage.DocumentFileStorage;
import edi.documents.storage.PartnerFolders;
import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentMdn;
import edi.documents.store.DocumentMdnRepository;
import edi.documents.store.DocumentRepository;
import edi.documents.store.DocumentStatus;
import edi.documents.store.PartnerCopyStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Busqueda, detalle, estadisticas y estado de las copias de los documentos (HU-14). */
@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final String CREATED_AT = "createdAt";
    static final int STATS_DAYS = 7;

    /** Filtros de busqueda; todos opcionales. Las fechas son dias completos en la zona del negocio. */
    public record Filter(String partner, Direction direction, String typeCode, DocumentStatus status, LocalDate from,
            LocalDate to, String messageId, String isaControl) {
    }

    public record PageView<T>(List<T> content, int number, int size, long totalElements, int totalPages) {
    }

    /** Donde esta cada una de las tres copias del documento. */
    public record StorageStatus(boolean database, boolean repositoryFile, String fileName, boolean partnerCopy,
            PartnerCopyStatus partnerCopyStatus) {
    }

    public record DayCount(LocalDate day, long count) {
    }

    public record Stats(long total, Map<String, Long> byStatus, Map<String, Long> byType,
            Map<String, Long> byDirection, List<DayCount> lastDays) {
    }

    public record MdnView(UUID documentId, String disposition, String receivedMic, Boolean micMatches,
            Boolean signatureValid, Instant receivedAt) {
    }

    private final DocumentRepository documents;
    private final DocumentMdnRepository mdns;
    private final DocumentFileStorage storage;
    private final PartnerFolders folders;
    private final Clock clock;
    private final ZoneId zone;

    public DocumentQueryService(DocumentRepository documents, DocumentMdnRepository mdns, DocumentFileStorage storage,
            PartnerFolders folders, Clock clock, @Value("${documents.zone:America/Guayaquil}") String zone) {
        this.documents = documents;
        this.mdns = mdns;
        this.storage = storage;
        this.folders = folders;
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public PageView<DocumentView> search(Filter f, int page, int size) {
        int s = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Page<Document> result = documents.findAll(spec(f),
                PageRequest.of(Math.max(page, 0), s, Sort.by(Sort.Direction.DESC, CREATED_AT)));
        return new PageView<>(result.map(DocumentView::of).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public List<DocumentView> recent(int limit) {
        return documents.findAll(PageRequest.of(0, Math.clamp(limit, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, CREATED_AT))).map(DocumentView::of).getContent();
    }

    public Document get(UUID id) {
        return documents.findById(id).orElseThrow(() -> new DocumentNotFoundException("No existe el documento " + id));
    }

    public DocumentView byMessageId(String messageId) {
        return documents.findByMessageId(messageId == null ? "" : messageId.strip()).map(DocumentView::of)
                .orElseThrow(() -> new DocumentNotFoundException("No hay documento con el Message-ID " + messageId));
    }

    public MdnView mdn(UUID id) {
        DocumentMdn m = mdns.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException("El documento " + id + " no tiene MDN"));
        return new MdnView(m.getDocumentId(), m.getDisposition(), m.getReceivedMic(), m.getMicMatches(),
                m.getSignatureValid(), m.getReceivedAt());
    }

    public StorageStatus storageStatus(UUID id) {
        Document d = get(id);
        return new StorageStatus(true, storage.exists(d.getFilePath()), DocumentView.of(d).fileName(),
                folders.present(d.getPartnerCopyPath()), d.getPartnerCopyStatus());
    }

    public Stats stats() {
        Map<String, Long> byStatus = counts(documents.countByStatus());
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = today.minusDays(STATS_DAYS - 1L);
        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (int i = 0; i < STATS_DAYS; i++) {
            perDay.put(first.plusDays(i), 0L);
        }
        for (Instant at : documents.createdSince(first.atStartOfDay(zone).toInstant())) {
            perDay.merge(at.atZone(zone).toLocalDate(), 1L, Long::sum);
        }
        List<DayCount> days = new ArrayList<>();
        perDay.forEach((day, n) -> days.add(new DayCount(day, n)));
        return new Stats(total, byStatus, counts(documents.countByType()), counts(documents.countByDirection()), days);
    }

    private static Map<String, Long> counts(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] r : rows) {
            m.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        return m;
    }

    private Specification<Document> spec(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (hasText(f.partner())) {
                p.add(cb.equal(cb.upper(root.get("partnerAs2Name")), f.partner().strip().toUpperCase()));
            }
            equal(p, cb, root, "direction", f.direction());
            equal(p, cb, root, "status", f.status());
            equal(p, cb, root, "typeCode", text(f.typeCode()));
            equal(p, cb, root, "messageId", text(f.messageId()));
            equal(p, cb, root, "isaControl", text(f.isaControl()));
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get(CREATED_AT), f.from().atStartOfDay(zone).toInstant()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get(CREATED_AT), f.to().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Agrega {@code campo = valor} si hay valor. */
    private static void equal(List<Predicate> p, CriteriaBuilder cb, Root<Document> root, String field, Object value) {
        if (value != null) {
            p.add(cb.equal(root.get(field), value));
        }
    }

    private static String text(String s) {
        return hasText(s) ? s.strip() : null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
