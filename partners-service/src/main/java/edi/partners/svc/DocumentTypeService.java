package edi.partners.svc;

import edi.partners.config.RequestIdentity;
import edi.partners.domain.DocumentType;
import edi.partners.domain.Partner;
import edi.partners.domain.PartnerDocumentType;
import edi.partners.dto.DocumentTypeRequest;
import edi.partners.dto.DocumentTypeResponse;
import edi.partners.dto.PartnerDocumentTypeRequest;
import edi.partners.dto.PartnerDocumentTypeResponse;
import edi.partners.error.DocumentTypeConflictException;
import edi.partners.error.DocumentTypeNotFoundException;
import edi.partners.repo.DocumentTypeRepository;
import edi.partners.repo.PartnerDocumentTypeRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditEvent.Change;
import edi.partners.svc.audit.AuditEventTypes;
import edi.partners.svc.audit.AuditLogPublisher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogo de tipos de documento EDI y tipos que intercambia cada socio (HU-13). Un tipo es unico por
 * estandar, codigo y version; uno asignado a algun socio no se puede borrar.
 */
@Service
public class DocumentTypeService {

    static final String DUPLICATE = "DOCUMENT_TYPE_DUPLICATE";
    static final String IN_USE = "DOCUMENT_TYPE_IN_USE";

    private final DocumentTypeRepository types;
    private final PartnerDocumentTypeRepository assignments;
    private final PartnerService partners;
    private final AuditLogPublisher audit;

    public DocumentTypeService(DocumentTypeRepository types, PartnerDocumentTypeRepository assignments,
            PartnerService partners, AuditLogPublisher audit) {
        this.types = types;
        this.assignments = assignments;
        this.partners = partners;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ catalogo

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> list() {
        Map<UUID, Long> usage = new HashMap<>();
        for (Object[] row : assignments.usageCounts()) {
            usage.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return types.findAllWithResponses().stream()
                .map(t -> DocumentTypeResponse.of(t, usage.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentTypeResponse get(UUID id) {
        DocumentType t = find(id);
        return DocumentTypeResponse.of(t, assignments.partnersUsing(id).size());
    }

    @Transactional
    public DocumentTypeResponse create(DocumentTypeRequest r) {
        String code = r.code().strip().toUpperCase(Locale.ROOT);
        String version = r.version().strip();
        rejectDuplicate(r, code, version, null);
        DocumentType t = new DocumentType(r.standard(), code, version, r.name().strip());
        t.setDescription(PartnerRules.trim(r.description()));
        if (r.responseTypeIds() != null) {
            t.getResponses().addAll(responses(r.responseTypeIds(), t.getId()));
        }
        t = types.saveAndFlush(t);
        publish(AuditEventTypes.DOCUMENT_TYPE_CREATED, "Tipo de documento registrado: " + t.label(),
                Map.of("responses", new Change(null, labels(t.getResponses()))));
        return DocumentTypeResponse.of(t, 0);
    }

    @Transactional
    public DocumentTypeResponse update(UUID id, DocumentTypeRequest r) {
        DocumentType t = find(id);
        String code = r.code().strip().toUpperCase(Locale.ROOT);
        String version = r.version().strip();
        rejectDuplicate(r, code, version, id);

        Map<String, Change> changes = new LinkedHashMap<>();
        track(changes, "standard", t.getStandard(), r.standard());
        track(changes, "code", t.getCode(), code);
        track(changes, "version", t.getVersion(), version);
        track(changes, "name", t.getName(), r.name().strip());
        track(changes, "description", t.getDescription(), PartnerRules.trim(r.description()));
        t.setStandard(r.standard());
        t.setCode(code);
        t.setVersion(version);
        t.setName(r.name().strip());
        t.setDescription(PartnerRules.trim(r.description()));
        if (r.responseTypeIds() != null) {
            String before = labels(t.getResponses());
            Set<DocumentType> next = responses(r.responseTypeIds(), id);
            t.getResponses().clear();
            t.getResponses().addAll(next);
            track(changes, "responses", before, labels(next));
        }
        t = types.saveAndFlush(t);
        publish(AuditEventTypes.DOCUMENT_TYPE_UPDATED, "Tipo de documento modificado: " + t.label(), changes);
        return DocumentTypeResponse.of(t, assignments.partnersUsing(id).size());
    }

    /** Borrado protegido: si algun socio lo usa responde 409 con sus identificadores AS2. */
    @Transactional
    public void delete(UUID id) {
        DocumentType t = find(id);
        List<String> using = assignments.partnersUsing(id);
        if (!using.isEmpty()) {
            throw new DocumentTypeConflictException(IN_USE, t.label() + " no se puede borrar: lo usan los socios "
                    + String.join(", ", using));
        }
        for (DocumentType other : types.findExpecting(id)) {
            other.getResponses().remove(t);
        }
        types.delete(t);
        publish(AuditEventTypes.DOCUMENT_TYPE_DELETED, "Tipo de documento borrado: " + t.label(), Map.of());
    }

    // ------------------------------------------------------------------ por socio

    @Transactional(readOnly = true)
    public List<PartnerDocumentTypeResponse> partnerTypes(UUID partnerId) {
        partners.any(partnerId);
        return assignments.findByPartner(partnerId).stream().map(PartnerDocumentTypeResponse::of).toList();
    }

    /** Reemplaza la lista completa de tipos del socio; la bitacora guarda la lista anterior y la nueva. */
    @Transactional
    public List<PartnerDocumentTypeResponse> replacePartnerTypes(UUID partnerId, PartnerDocumentTypeRequest r) {
        Partner p = partners.active(partnerId);
        Set<String> seen = new HashSet<>();
        for (PartnerDocumentTypeRequest.Item item : r.documentTypes()) {
            if (!seen.add(item.documentTypeId() + "/" + item.direction())) {
                throw new IllegalArgumentException("Un tipo de documento aparece dos veces con la misma direccion");
            }
        }
        List<PartnerDocumentType> existing = assignments.findByPartner(partnerId);
        String before = summary(existing);
        assignments.deleteAll(existing);
        assignments.flush();

        List<PartnerDocumentType> next = r.documentTypes().stream()
                .map(item -> new PartnerDocumentType(p, find(item.documentTypeId()), item.direction(),
                        PartnerRules.trim(item.notes()), cleanFields(item.extraFields())))
                .toList();
        assignments.saveAllAndFlush(next);
        String after = summary(next);
        if (!Objects.equals(before, after)) {
            audit.publish(AuditEvent.of(AuditEventTypes.PARTNER_DOCUMENT_TYPES_UPDATED, p.getId(), p.getAs2Name(),
                    RequestIdentity.currentUser(), "Tipos de documento del socio guardados",
                    Map.of("documentTypes", new Change(before, after))));
        }
        return assignments.findByPartner(partnerId).stream().map(PartnerDocumentTypeResponse::of).toList();
    }

    // ------------------------------------------------------------------ apoyo

    private DocumentType find(UUID id) {
        return types.findById(id).orElseThrow(() -> new DocumentTypeNotFoundException(id));
    }

    private void rejectDuplicate(DocumentTypeRequest r, String code, String version, UUID self) {
        types.findByStandardAndCodeAndVersion(r.standard(), code, version)
                .filter(other -> !other.getId().equals(self))
                .ifPresent(other -> {
                    throw new DocumentTypeConflictException(DUPLICATE, "Ya existe " + other.label() + " en el catalogo");
                });
    }

    private Set<DocumentType> responses(List<UUID> ids, UUID self) {
        Set<DocumentType> out = new LinkedHashSet<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            if (id.equals(self)) {
                throw new IllegalArgumentException("Un tipo de documento no puede ser respuesta de si mismo");
            }
            out.add(types.findById(id).orElseThrow(
                    () -> new IllegalArgumentException("El tipo de respuesta " + id + " no existe en el catalogo")));
        }
        return out;
    }

    /** Sin nombres vacios; los valores nulos quedan como texto vacio. */
    static Map<String, String> cleanFields(Map<String, String> fields) {
        Map<String, String> out = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    out.put(k.strip(), v == null ? "" : v.strip());
                }
            });
        }
        return out;
    }

    private static String labels(Set<DocumentType> set) {
        return set.stream().map(DocumentType::label).sorted().collect(Collectors.joining(", "));
    }

    /** "X12 850 4010 INBOUND, X12 856 4010 OUTBOUND" para la bitacora. */
    private static String summary(List<PartnerDocumentType> list) {
        return list.stream()
                .map(a -> a.getDocumentType().label() + " " + a.getDirection())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static void track(Map<String, Change> changes, String field, Object from, Object to) {
        Object a = from instanceof Enum<?> e ? e.name() : from;
        Object b = to instanceof Enum<?> e ? e.name() : to;
        if (!Objects.equals(a, b)) {
            changes.put(field, new Change(a, b));
        }
    }

    private void publish(String eventType, String detail, Map<String, Change> changes) {
        audit.publish(AuditEvent.of(eventType, null, null, RequestIdentity.currentUser(), detail, changes));
    }
}
