package edi.partners.svc;

import edi.partners.config.RequestIdentity;
import edi.partners.domain.KeyUsage;
import edi.partners.domain.Partner;
import edi.partners.domain.PartnerCertificate;
import edi.partners.dto.CertificateChangeResponse;
import edi.partners.dto.CertificateResponse;
import edi.partners.dto.CertificateUploadResponse;
import edi.partners.dto.CertificateUploadResponse.Outcome;
import edi.partners.dto.InternalCertificateView;
import edi.partners.error.CertificateNotFoundException;
import edi.partners.error.PartnerStateException;
import edi.partners.repo.PartnerCertificateRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditEvent.Change;
import edi.partners.svc.audit.AuditEventTypes;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.certs.X509Reader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Certificados X.509 de los socios con doble compuerta (HU-11, ADR-08). Al cargar un certificado
 * nuevo, este queda activo y preferente y los anteriores del mismo uso siguen activos como respaldo:
 * asi el intercambio no se corta si el socio rota su clave antes o despues de lo anunciado. Como
 * maximo hay un preferente por socio y uso (RN-12), y mientras quede alguno activo, uno lo es.
 * Nunca se acepta una clave privada (RN-14).
 */
@Service
public class PartnerCertificateService {

    private static final Logger log = LoggerFactory.getLogger(PartnerCertificateService.class);

    /** RN-13: el motor cifra como maximo para este numero de certificados activos. */
    static final int MAX_ENCRYPTION_RECIPIENTS = 3;

    /** Orden de la consola: por uso, el preferente primero y luego el que mas tarde vence. */
    private static final Comparator<PartnerCertificate> DISPLAY_ORDER =
            Comparator.comparing(PartnerCertificate::getKeyUsage)
                    .thenComparing(PartnerCertificate::isActive, Comparator.reverseOrder())
                    .thenComparing(PartnerCertificate::isPreferred, Comparator.reverseOrder())
                    .thenComparing(PartnerCertificate::getValidTo, Comparator.reverseOrder());

    private final PartnerService partners;
    private final PartnerCertificateRepository repo;
    private final AuditLogPublisher audit;
    private final Clock clock;

    public PartnerCertificateService(PartnerService partners, PartnerCertificateRepository repo,
                                     AuditLogPublisher audit, Clock clock) {
        this.partners = partners;
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public List<CertificateResponse> list(UUID partnerId) {
        partners.active(partnerId);
        Instant now = clock.instant();
        return repo.findAllByPartnerId(partnerId).stream().sorted(DISPLAY_ORDER)
                .map(c -> CertificateResponse.of(c, now, false)).toList();
    }

    @Transactional(readOnly = true)
    public CertificateResponse get(UUID partnerId, UUID certificateId) {
        return CertificateResponse.of(find(partnerId, certificateId), clock.instant(), true);
    }

    /** PEM para descargar, con el nombre de archivo sugerido. */
    @Transactional(readOnly = true)
    public PemFile pem(UUID partnerId, UUID certificateId) {
        PartnerCertificate c = find(partnerId, certificateId);
        String name = (c.getPartner().getAs2Name() + "_" + c.getKeyUsage().name().toLowerCase() + "_"
                + c.getFingerprintSha256().substring(0, 8) + ".pem").replaceAll("[^A-Za-z0-9._-]", "_");
        return new PemFile(name, c.getCertPem());
    }

    /** Archivo PEM de un certificado. */
    public record PemFile(String fileName, String content) {
    }

    // ------------------------------------------------------------------ carga

    @Transactional
    public CertificateUploadResponse upload(UUID partnerId, KeyUsage usage, byte[] content, String description) {
        Partner p = partners.active(partnerId);
        if (usage == null) {
            throw new IllegalArgumentException("Indique el uso del certificado: ENCRYPTION, SIGNATURE o SSL");
        }
        X509Reader.Parsed parsed = X509Reader.read(content);
        Instant now = clock.instant();
        validate(parsed, usage, now);

        Optional<PartnerCertificate> existing = repo.findByPartnerIdAndKeyUsageAndFingerprintSha256(
                partnerId, usage, parsed.data().fingerprintSha256());
        if (existing.isPresent()) {
            return reupload(p, existing.get(), now);
        }

        PartnerCertificate c = new PartnerCertificate(p, usage, parsed.data());
        c.setDescription(clean(description));
        List<PartnerCertificate> backups = repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, usage);
        clearPreferred(backups);
        c.setPreferred(true);
        repo.saveAndFlush(c);

        Map<String, Change> changes = new LinkedHashMap<>();
        changes.put("keyUsage", new Change(null, usage));
        changes.put("issuedTo", new Change(null, c.getIssuedTo()));
        changes.put("fingerprintSha256", new Change(null, c.getFingerprintSha256()));
        changes.put("validTo", new Change(null, c.getValidTo()));
        changes.put("preferred", new Change(null, true));
        publish(AuditEventTypes.CERTIFICATE_UPLOADED, p, "Certificado de " + label(usage) + " cargado como preferente: "
                + describe(c) + (backups.isEmpty() ? "" : "; quedan " + backups.size() + " de respaldo"), changes);
        return new CertificateUploadResponse(CertificateResponse.of(c, now, false), Outcome.CREATED,
                warnings(partnerId, usage));
    }

    /** El mismo certificado ya estaba cargado: si esta activo no cambia nada; si no, vuelve como respaldo. */
    private CertificateUploadResponse reupload(Partner p, PartnerCertificate c, Instant now) {
        if (c.isActive()) {
            return new CertificateUploadResponse(CertificateResponse.of(c, now, false), Outcome.ALREADY_ACTIVE,
                    warnings(p.getId(), c.getKeyUsage()));
        }
        c.setActive(true);
        repo.saveAndFlush(c);
        ensurePreferred(p.getId(), c.getKeyUsage(), now);
        publish(AuditEventTypes.CERTIFICATE_REACTIVATED, p, "Certificado de " + label(c.getKeyUsage())
                + " cargado de nuevo, activo como " + role(c) + ": " + describe(c),
                Map.of("active", new Change(false, true)));
        return new CertificateUploadResponse(CertificateResponse.of(c, now, false), Outcome.REACTIVATED,
                warnings(p.getId(), c.getKeyUsage()));
    }

    private static void validate(X509Reader.Parsed parsed, KeyUsage usage, Instant now) {
        if (!parsed.data().validTo().isAfter(parsed.data().validFrom())) {
            throw new IllegalArgumentException(
                    "Las fechas de vigencia del certificado son incoherentes: termina antes de empezar");
        }
        if (!parsed.data().validTo().isAfter(now)) {
            throw new IllegalArgumentException("El certificado ya está expirado");
        }
        if (usage == KeyUsage.SSL) {
            return; // la confianza TLS admite la CA del socio
        }
        if (parsed.isCa()) {
            throw new IllegalArgumentException("El certificado es de una CA (no entidad final)");
        }
        if (usage == KeyUsage.ENCRYPTION && !parsed.allowsEncryption()) {
            throw new IllegalArgumentException(
                    "El certificado no permite cifrar: su uso de clave no incluye keyEncipherment ni dataEncipherment");
        }
        if (usage == KeyUsage.SIGNATURE && !parsed.allowsSignature()) {
            throw new IllegalArgumentException(
                    "El certificado no permite verificar firmas: su uso de clave no incluye digitalSignature ni nonRepudiation");
        }
    }

    // ------------------------------------------------------------------ doble compuerta

    @Transactional
    public CertificateChangeResponse prefer(UUID partnerId, UUID certificateId) {
        PartnerCertificate c = find(partnerId, certificateId);
        Instant now = clock.instant();
        if (!c.isActive()) {
            throw new PartnerStateException("Active el certificado antes de marcarlo como preferente");
        }
        if (c.isExpired(now)) {
            throw new PartnerStateException("No se puede marcar como preferente un certificado expirado");
        }
        if (!c.isPreferred()) {
            clearPreferred(repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, c.getKeyUsage()));
            c.setPreferred(true);
            repo.saveAndFlush(c);
            publish(AuditEventTypes.CERTIFICATE_PREFERRED, c.getPartner(), "Certificado de " + label(c.getKeyUsage())
                    + " marcado como preferente: " + describe(c), Map.of("preferred", new Change(false, true)));
        }
        return sameUsage(partnerId, c.getKeyUsage(), now);
    }

    @Transactional
    public CertificateChangeResponse activate(UUID partnerId, UUID certificateId) {
        PartnerCertificate c = find(partnerId, certificateId);
        Instant now = clock.instant();
        if (!c.isActive()) {
            if (c.isExpired(now)) {
                throw new PartnerStateException("El certificado ya está expirado");
            }
            c.setActive(true);
            repo.saveAndFlush(c);
            ensurePreferred(partnerId, c.getKeyUsage(), now);
            publish(AuditEventTypes.CERTIFICATE_ACTIVATED, c.getPartner(), "Certificado de " + label(c.getKeyUsage())
                    + " activado como " + role(c) + ": " + describe(c), Map.of("active", new Change(false, true)));
        }
        return sameUsage(partnerId, c.getKeyUsage(), now);
    }

    /**
     * Desactivar el unico activo de un uso se permite (la consola pide confirmacion), pero la
     * respuesta avisa que los intercambios que lo necesitan fallaran hasta cargar otro.
     */
    @Transactional
    public CertificateChangeResponse deactivate(UUID partnerId, UUID certificateId) {
        PartnerCertificate c = find(partnerId, certificateId);
        Instant now = clock.instant();
        if (c.isActive()) {
            boolean wasPreferred = c.isPreferred();
            c.setActive(false);
            repo.saveAndFlush(c);
            Optional<PartnerCertificate> promoted = ensurePreferred(partnerId, c.getKeyUsage(), now);
            Map<String, Change> changes = new LinkedHashMap<>();
            changes.put("active", new Change(true, false));
            if (wasPreferred) {
                changes.put("preferred", new Change(true, false));
            }
            publish(AuditEventTypes.CERTIFICATE_DEACTIVATED, c.getPartner(), "Certificado de " + label(c.getKeyUsage())
                    + " desactivado: " + describe(c) + promotedText(promoted), changes);
        }
        return sameUsage(partnerId, c.getKeyUsage(), now);
    }

    @Transactional
    public CertificateChangeResponse delete(UUID partnerId, UUID certificateId) {
        PartnerCertificate c = find(partnerId, certificateId);
        Instant now = clock.instant();
        KeyUsage usage = c.getKeyUsage();
        Partner p = c.getPartner();
        String what = describe(c);
        Map<String, Change> changes = Map.of("fingerprintSha256", new Change(c.getFingerprintSha256(), null));
        repo.delete(c);
        repo.flush();
        Optional<PartnerCertificate> promoted = ensurePreferred(partnerId, usage, now);
        publish(AuditEventTypes.CERTIFICATE_DELETED, p, "Certificado de " + label(usage) + " eliminado: " + what
                + promotedText(promoted), changes);
        return sameUsage(partnerId, usage, now);
    }

    @Transactional
    public CertificateResponse update(UUID partnerId, UUID certificateId, String description) {
        PartnerCertificate c = find(partnerId, certificateId);
        String value = clean(description);
        if (!Objects.equals(value, c.getDescription())) {
            Change change = new Change(c.getDescription(), value);
            c.setDescription(value);
            repo.saveAndFlush(c);
            publish(AuditEventTypes.CERTIFICATE_UPDATED, c.getPartner(), "Descripcion del certificado actualizada: "
                    + describe(c), Map.of("description", change));
        }
        return CertificateResponse.of(c, clock.instant(), false);
    }

    // ------------------------------------------------------------------ retiro diario

    /** Resultado del retiro: cuantos se desactivaron y cuantos se conservaron por ser el unico activo. */
    public record RetirementResult(int retired, int kept) {
    }

    /**
     * Desactiva los certificados activos ya caducados si queda otro vigente del mismo uso, y promueve
     * a preferente el que mas tarde vence. El unico activo de un uso se conserva aunque haya caducado,
     * para no romper lo que hoy funciona, y queda una advertencia en la bitacora (el aviso por correo
     * es de HU-21).
     */
    @Transactional
    public RetirementResult retireExpired(Instant now) {
        Map<String, List<PartnerCertificate>> groups = new LinkedHashMap<>();
        for (PartnerCertificate c : repo.findActiveExpired(now)) {
            groups.computeIfAbsent(c.getPartner().getId() + "/" + c.getKeyUsage(), k -> new ArrayList<>()).add(c);
        }
        int retired = 0;
        int kept = 0;
        for (List<PartnerCertificate> expired : groups.values()) {
            PartnerCertificate first = expired.get(0);
            UUID partnerId = first.getPartner().getId();
            KeyUsage usage = first.getKeyUsage();
            boolean anotherValid = repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, usage).stream()
                    .anyMatch(c -> !c.isExpired(now));
            if (!anotherValid) {
                for (PartnerCertificate c : expired) {
                    kept++;
                    log.warn("Certificado de {} del socio {} caducado y sin reemplazo vigente: se conserva activo",
                            label(usage), c.getPartner().getAs2Name());
                    audit.publish(new AuditEvent(AuditEventTypes.CERTIFICATE_EXPIRED, partnerId,
                            c.getPartner().getAs2Name(), null, RequestIdentity.SYSTEM, false,
                            "Unico certificado activo de " + label(usage) + " caducado; se conserva hasta cargar otro: "
                                    + describe(c), Map.of(), AuditEvent.THIS_SERVICE));
                }
                continue;
            }
            for (PartnerCertificate c : expired) {
                boolean wasPreferred = c.isPreferred();
                c.setActive(false);
                repo.saveAndFlush(c);
                retired++;
                Map<String, Change> changes = new LinkedHashMap<>();
                changes.put("active", new Change(true, false));
                if (wasPreferred) {
                    changes.put("preferred", new Change(true, false));
                }
                audit.publish(AuditEvent.of(AuditEventTypes.CERTIFICATE_RETIRED, partnerId, c.getPartner().getAs2Name(),
                        RequestIdentity.SYSTEM, "Certificado de " + label(usage) + " caducado retirado: " + describe(c),
                        changes));
            }
            ensurePreferred(partnerId, usage, now).ifPresent(promoted -> log.info(
                    "Certificado {} promovido a preferente tras el retiro", promoted.getFingerprintSha256()));
        }
        return new RetirementResult(retired, kept);
    }

    // ------------------------------------------------------------------ motor AS2 (red interna)

    /** Certificados activos de un socio y uso, el preferente primero (HU-15 cifra para todos). */
    @Transactional(readOnly = true)
    public List<InternalCertificateView> activeForEngine(UUID partnerId, KeyUsage usage) {
        partners.active(partnerId);
        return repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, usage).stream().sorted(DISPLAY_ORDER)
                .map(InternalCertificateView::of).toList();
    }

    @Transactional(readOnly = true)
    public InternalCertificateView forEngine(UUID certificateId) {
        return repo.findById(certificateId).map(InternalCertificateView::of)
                .orElseThrow(() -> new CertificateNotFoundException(certificateId));
    }

    // ------------------------------------------------------------------ apoyo

    private PartnerCertificate find(UUID partnerId, UUID certificateId) {
        partners.active(partnerId);
        return repo.findByIdAndPartnerId(certificateId, partnerId)
                .orElseThrow(() -> new CertificateNotFoundException(certificateId));
    }

    /** Quita la marca de preferente y la escribe ya: el indice unico parcial no admite dos a la vez. */
    private void clearPreferred(List<PartnerCertificate> certificates) {
        for (PartnerCertificate other : certificates) {
            if (other.isPreferred()) {
                other.setPreferred(false);
                repo.saveAndFlush(other);
            }
        }
    }

    /**
     * Si quedan activos y ninguno es preferente, promueve el vigente que mas tarde vence (o, si todos
     * caducaron, el que mas tarde vencio). Devuelve el promovido, si lo hubo.
     */
    private Optional<PartnerCertificate> ensurePreferred(UUID partnerId, KeyUsage usage, Instant now) {
        List<PartnerCertificate> actives = repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, usage);
        if (actives.isEmpty() || actives.stream().anyMatch(PartnerCertificate::isPreferred)) {
            return Optional.empty();
        }
        Comparator<PartnerCertificate> best = Comparator.comparing((PartnerCertificate c) -> !c.isExpired(now))
                .thenComparing(PartnerCertificate::getValidTo);
        PartnerCertificate promoted = actives.stream().max(best).orElseThrow();
        promoted.setPreferred(true);
        repo.saveAndFlush(promoted);
        return Optional.of(promoted);
    }

    private CertificateChangeResponse sameUsage(UUID partnerId, KeyUsage usage, Instant now) {
        List<CertificateResponse> all = repo.findAllByPartnerIdAndKeyUsage(partnerId, usage).stream()
                .sorted(DISPLAY_ORDER).map(c -> CertificateResponse.of(c, now, false)).toList();
        return new CertificateChangeResponse(all, warnings(partnerId, usage));
    }

    private List<String> warnings(UUID partnerId, KeyUsage usage) {
        long active = repo.findAllByPartnerIdAndKeyUsageAndActiveTrue(partnerId, usage).size();
        List<String> warnings = new ArrayList<>();
        if (active == 0) {
            warnings.add("No queda ningun certificado de " + label(usage) + " activo: " + effectWithout(usage));
        }
        if (usage == KeyUsage.ENCRYPTION && active > MAX_ENCRYPTION_RECIPIENTS) {
            warnings.add("Hay " + active + " certificados de cifrado activos y el motor cifra como maximo para "
                    + MAX_ENCRYPTION_RECIPIENTS + ": desactive los que el socio ya no use");
        }
        return warnings;
    }

    private static String effectWithout(KeyUsage usage) {
        return switch (usage) {
            case ENCRYPTION -> "los envios a este socio fallaran hasta cargar otro";
            case SIGNATURE -> "no se podra verificar la firma de lo que envie el socio";
            case SSL -> "la conexion HTTPS con el socio solo confiara en las autoridades del sistema";
        };
    }

    static String label(KeyUsage usage) {
        return switch (usage) {
            case ENCRYPTION -> "cifrado";
            case SIGNATURE -> "firma";
            case SSL -> "TLS";
        };
    }

    private static String role(PartnerCertificate c) {
        return c.isPreferred() ? "preferente" : "respaldo";
    }

    private static String describe(PartnerCertificate c) {
        return c.getIssuedTo() + " (huella " + c.getFingerprintSha256().substring(0, 16) + "…, vence "
                + c.getValidTo() + ")";
    }

    private static String promotedText(Optional<PartnerCertificate> promoted) {
        return promoted.map(p -> "; nuevo preferente: " + describe(p)).orElse("");
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private void publish(String type, Partner p, String detail, Map<String, Change> changes) {
        audit.publish(AuditEvent.of(type, p.getId(), p.getAs2Name(), RequestIdentity.currentUser(), detail, changes));
    }
}
