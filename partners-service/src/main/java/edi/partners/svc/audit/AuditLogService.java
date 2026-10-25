package edi.partners.svc.audit;

import edi.partners.domain.AuditLogEntry;
import edi.partners.domain.AuditStatus;
import edi.partners.domain.Partner;
import edi.partners.dto.AuditLogQuery;
import edi.partners.dto.AuditLogRequest;
import edi.partners.dto.AuditLogResponse;
import edi.partners.repo.AuditLogRepository;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.PartnerRules;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de la bitacora y registro de los eventos que envian otros servicios. */
@Service
public class AuditLogService {

    /** Tope de una pagina de la consola y de una exportacion. */
    public static final int MAX_PAGE = 200;
    public static final int MAX_EXPORT = 10_000;

    private final AuditLogRepository repo;
    private final PartnerRepository partners;
    private final AuditLogPublisher publisher;

    public AuditLogService(AuditLogRepository repo, PartnerRepository partners, AuditLogPublisher publisher) {
        this.repo = repo;
        this.partners = partners;
        this.publisher = publisher;
    }

    /**
     * Eventos que cumplen los filtros, del mas reciente al mas antiguo. Al buscar un Message-ID el
     * orden es cronologico: se lee como la historia de ese intercambio.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogQuery q, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE), order(q));
        return repo.findAll(spec(q), pageable).map(AuditLogResponse::of);
    }

    /** Lo mismo que {@link #search} sin paginar, hasta {@link #MAX_EXPORT} eventos. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> export(AuditLogQuery q) {
        return repo.findAll(spec(q), PageRequest.of(0, MAX_EXPORT, order(q))).map(AuditLogResponse::of).getContent();
    }

    @Transactional(readOnly = true)
    public List<String> eventTypes() {
        return repo.findEventTypes();
    }

    /** Evento de otro servicio. Si llega solo el identificador AS2 de un socio activo, se completa su id. */
    @Transactional
    public AuditLogResponse register(AuditLogRequest r) {
        String as2Name = PartnerRules.trim(r.partnerAs2Name());
        var partnerId = r.partnerId();
        if (partnerId == null && as2Name != null) {
            partnerId = partners.findByAs2NameIgnoreCaseAndActiveTrue(as2Name).map(Partner::getId).orElse(null);
        }
        Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
        if (r.changes() != null) {
            r.changes().forEach((k, v) -> changes.put(k, new AuditEvent.Change(v.from(), v.to())));
        }
        String actor = PartnerRules.trim(r.actor());
        AuditLogEntry saved = publisher.publish(new AuditEvent(r.eventType(), partnerId, as2Name,
                PartnerRules.trim(r.messageId()), actor == null ? "system" : actor, r.status() == AuditStatus.SUCCESS,
                PartnerRules.trim(r.detail()), changes, r.source().strip()));
        return AuditLogResponse.of(saved);
    }

    private static final String OCCURRED_AT = "occurredAt";

    private static Sort order(AuditLogQuery q) {
        Sort.Direction dir = present(q.messageId()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, OCCURRED_AT).and(Sort.by(dir, "id"));
    }

    static Specification<AuditLogEntry> spec(AuditLogQuery q) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            equal(where, cb, root.get("partnerId"), q.partnerId());
            equalIgnoringCase(where, cb, root.get("partnerAs2Name"), q.partnerAs2Name());
            if (present(q.eventType())) {
                where.add(cb.equal(root.get("eventType"), q.eventType().strip().toUpperCase(Locale.ROOT)));
            }
            equal(where, cb, root.get("status"), q.status());
            equalIgnoringCase(where, cb, root.get("actor"), q.actor());
            equal(where, cb, root.get("messageId"), present(q.messageId()) ? q.messageId().strip() : null);
            if (q.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get(OCCURRED_AT), q.from()));
            }
            if (q.to() != null) {
                where.add(cb.lessThan(root.get(OCCURRED_AT), q.to()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static void equal(List<Predicate> where, CriteriaBuilder cb, Expression<?> field, Object value) {
        if (value != null) {
            where.add(cb.equal(field, value));
        }
    }

    private static void equalIgnoringCase(List<Predicate> where, CriteriaBuilder cb, Expression<String> field,
                                          String value) {
        if (present(value)) {
            where.add(cb.equal(cb.lower(field), lower(value)));
        }
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String lower(String s) {
        return s.strip().toLowerCase(Locale.ROOT);
    }
}
