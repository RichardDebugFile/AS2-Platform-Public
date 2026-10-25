package edi.partners.svc;

import edi.partners.config.RequestIdentity;
import edi.partners.domain.MdnMode;
import edi.partners.domain.Partner;
import edi.partners.domain.TransportType;
import edi.partners.dto.PartnerRequest;
import edi.partners.dto.PartnerResponse;
import edi.partners.error.DuplicateAs2NameException;
import edi.partners.error.PartnerNotFoundException;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.folders.PartnerFolderProvisioner;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edicion, consulta y baja de socios. El identificador AS2 es unico entre los activos; la
 * baja es logica y libera el identificador. El borrado definitivo exige confirmar el identificador.
 */
@Service
public class PartnerService {

    private final PartnerRepository repo;
    private final CryptoService crypto;
    private final AuditLogPublisher audit;
    private final PartnerFolderProvisioner folders;

    public PartnerService(PartnerRepository repo, CryptoService crypto, AuditLogPublisher audit,
                          PartnerFolderProvisioner folders) {
        this.repo = repo;
        this.crypto = crypto;
        this.audit = audit;
        this.folders = folders;
    }

    /** Crea el socio y sus carpetas en la misma transaccion: si las carpetas fallan, no queda nada. */
    @Transactional
    public Partner create(PartnerRequest r) {
        String as2Name = PartnerRules.as2Name(r.as2Name());
        if (repo.existsByAs2NameIgnoreCaseAndActiveTrue(as2Name)) {
            throw new DuplicateAs2NameException(as2Name);
        }
        Partner p = new Partner();
        p.setAs2Name(as2Name);
        apply(p, r, true);
        p = repo.saveAndFlush(p);
        folders.provision(p);
        publish("partners:CREATE", p.getId(), "Socio creado: " + as2Name);
        return p;
    }

    @Transactional
    public PartnerResponse update(UUID id, PartnerRequest r) {
        Partner p = active(id);
        String as2Name = PartnerRules.as2Name(r.as2Name());
        if (!as2Name.equalsIgnoreCase(p.getAs2Name())
                && repo.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot(as2Name, id)) {
            throw new DuplicateAs2NameException(as2Name);
        }
        String before = p.getAs2Name();
        p.setAs2Name(as2Name);
        apply(p, r, false);
        repo.saveAndFlush(p);
        publish("partners:UPDATE", id, before.equals(as2Name)
                ? "Socio actualizado: " + as2Name
                : "Socio actualizado: " + before + " -> " + as2Name);
        return PartnerResponse.of(p);
    }

    /** Activa o desactiva. Reactivar falla si otro socio activo ya usa el identificador. */
    @Transactional
    public PartnerResponse setActive(UUID id, boolean active) {
        return changeActive(any(id), active);
    }

    /** Baja logica: el socio deja de operar pero conserva su historial. */
    @Transactional
    public void delete(UUID id) {
        changeActive(active(id), false);
    }

    private PartnerResponse changeActive(Partner p, boolean active) {
        UUID id = p.getId();
        if (active && !p.isActive() && repo.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot(p.getAs2Name(), id)) {
            throw new DuplicateAs2NameException(p.getAs2Name());
        }
        if (p.isActive() != active) {
            p.setActive(active);
            repo.saveAndFlush(p);
            publish(active ? "partners:ACTIVATE" : "partners:DEACTIVATE", id,
                    (active ? "Socio activado: " : "Socio desactivado: ") + p.getAs2Name());
        }
        return PartnerResponse.of(p);
    }

    /**
     * Borrado definitivo, con sus configuraciones (cascada). Exige {@code confirm} igual al
     * identificador AS2 para evitar borrados por error. Solo ADMIN (lo exige el gateway).
     */
    @Transactional
    public void purge(UUID id, String confirm) {
        Partner p = any(id);
        if (confirm == null || !confirm.strip().equals(p.getAs2Name())) {
            throw new IllegalArgumentException(
                    "Para borrar definitivamente escriba el identificador AS2 del socio: " + p.getAs2Name());
        }
        publish("partners:PURGE", id, "Socio borrado definitivamente: " + p.getAs2Name());
        repo.delete(p);
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> list(boolean active) {
        return repo.findAllByActiveOrderByAs2NameAsc(active).stream().map(PartnerResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public PartnerResponse get(UUID id) {
        return PartnerResponse.of(any(id));
    }

    @Transactional(readOnly = true)
    public Partner findActiveByAs2Name(String as2Name) {
        return repo.findByAs2NameIgnoreCaseAndActiveTrue(as2Name == null ? "" : as2Name.strip())
                .orElseThrow(() -> new PartnerNotFoundException(as2Name));
    }

    Partner any(UUID id) {
        return repo.findById(id).orElseThrow(() -> new PartnerNotFoundException(id));
    }

    Partner active(UUID id) {
        Partner p = any(id);
        if (!p.isActive()) {
            throw new PartnerNotFoundException(id);
        }
        return p;
    }

    /**
     * Copia la peticion sobre la entidad. En el alta, lo que no viene toma el valor por defecto de la
     * entidad; en la edicion, los indicadores en {@code null} conservan su valor.
     */
    private void apply(Partner p, PartnerRequest r, boolean creating) {
        p.setPartnerName(PartnerRules.trim(r.partnerName()));
        p.setPartnerAs2Name(PartnerRules.trim(r.partnerAs2Name()));
        p.setPartnerType(r.partnerType());
        p.setExternalId(PartnerRules.trim(r.externalId()));
        p.setDescription(PartnerRules.trim(r.description()));

        p.setServerAddress(PartnerRules.serverAddress(r.serverAddress()));
        p.setPort(r.port());
        if (r.transportType() != null || creating) {
            p.setTransportType(r.transportType() == null ? TransportType.HTTPS : r.transportType());
        }
        p.setPath(PartnerRules.path(r.path()));
        applyBasicAuth(p, r);
        p.setConnectionTimeoutSeconds(r.connectionTimeoutSeconds());
        p.setReadTimeoutSeconds(r.readTimeoutSeconds());
        p.setMaxRetryAttempts(r.maxRetryAttempts());
        p.setRetryBackoffMs(r.retryBackoffMs());

        p.setSigningAlgorithm(r.signingAlgorithm());
        p.setEncryptionAlgorithm(r.encryptionAlgorithm());
        p.setMicAlgorithm(r.micAlgorithm());
        p.setCompressionEnabled(or(r.compressionEnabled(), p.isCompressionEnabled()));
        p.setRequestMdn(or(r.requestMdn(), p.isRequestMdn()));
        p.setRequestSignedMdn(or(r.requestSignedMdn(), p.isRequestSignedMdn()));
        if (r.mdnMode() != null) {
            p.setMdnMode(r.mdnMode());
        }
        p.setMdnReceiptUrl(PartnerRules.mdnUrl(p.getMdnMode(), r.mdnReceiptUrl(), "La URL de recepcion del MDN"));
        if (p.getMdnMode() == MdnMode.ASYNC && !p.isRequestMdn()) {
            throw new IllegalArgumentException("El MDN asincrono requiere solicitar MDN");
        }
        p.setValidateCertificate(or(r.validateCertificate(), p.isValidateCertificate()));
        p.setValidateSignature(or(r.validateSignature(), p.isValidateSignature()));
        p.setValidateMic(or(r.validateMic(), p.isValidateMic()));

        p.setEnableDualStorage(or(r.enableDualStorage(), p.isEnableDualStorage()));
        p.setEnableIncomingQueue(or(r.enableIncomingQueue(), p.isEnableIncomingQueue()));
        p.setSharedFolderName(PartnerRules.trim(r.sharedFolderName()));
        p.setDataLocation(PartnerRules.trim(r.dataLocation()));

        p.setContactName(PartnerRules.trim(r.contactName()));
        p.setContactPhone(PartnerRules.trim(r.contactPhone()));
        p.setContactEmail(PartnerRules.trim(r.contactEmail()));
        p.setTradingPartnerEmail(PartnerRules.trim(r.tradingPartnerEmail()));
        p.setAlertOnFailure(or(r.alertOnFailure(), p.isAlertOnFailure()));
    }

    /** Sin usuario no hay clave; una clave vacia en la edicion conserva la guardada. */
    private void applyBasicAuth(Partner p, PartnerRequest r) {
        String user = PartnerRules.trim(r.basicAuthUsername());
        p.setBasicAuthUsername(user);
        if (user == null) {
            p.setBasicAuthPasswordEnc(null);
        } else if (r.basicAuthPassword() != null && !r.basicAuthPassword().isBlank()) {
            p.setBasicAuthPasswordEnc(crypto.encrypt(r.basicAuthPassword()));
        }
    }

    private static boolean or(Boolean value, boolean current) {
        return value == null ? current : value;
    }

    private void publish(String action, UUID partnerId, String detail) {
        audit.publish(new AuditEvent(action, partnerId, RequestIdentity.currentUser(), true, detail));
    }
}
