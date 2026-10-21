package edi.partners.svc;

import edi.partners.config.RequestIdentity;
import edi.partners.domain.ArchiveMode;
import edi.partners.domain.IncomingConfig;
import edi.partners.domain.MdnConfig;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import edi.partners.domain.MimeType;
import edi.partners.domain.OutgoingConfig;
import edi.partners.domain.Partner;
import edi.partners.domain.SendingMode;
import edi.partners.dto.IncomingConfigRequest;
import edi.partners.dto.IncomingConfigResponse;
import edi.partners.dto.MdnConfigRequest;
import edi.partners.dto.MdnConfigResponse;
import edi.partners.dto.OutgoingConfigRequest;
import edi.partners.dto.OutgoingConfigResponse;
import edi.partners.error.ConfigNotFoundException;
import edi.partners.repo.IncomingConfigRepository;
import edi.partners.repo.MdnConfigRepository;
import edi.partners.repo.OutgoingConfigRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditLogPublisher;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuraciones de salida, entrada y MDN de cada socio (una de cada tipo). {@code upsert} crea o
 * reemplaza; las reglas de cada configuracion se validan aqui.
 */
@Service
public class PartnerConfigService {

    private final PartnerService partners;
    private final OutgoingConfigRepository outgoing;
    private final IncomingConfigRepository incoming;
    private final MdnConfigRepository mdn;
    private final StorageLayout storage;
    private final AuditLogPublisher audit;

    public PartnerConfigService(PartnerService partners, OutgoingConfigRepository outgoing,
                                IncomingConfigRepository incoming, MdnConfigRepository mdn,
                                StorageLayout storage, AuditLogPublisher audit) {
        this.partners = partners;
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.mdn = mdn;
        this.storage = storage;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ salida

    @Transactional(readOnly = true)
    public OutgoingConfigResponse getOutgoing(UUID partnerId) {
        partners.any(partnerId);
        return outgoing.findByPartnerId(partnerId).map(OutgoingConfigResponse::of)
                .orElseThrow(() -> new ConfigNotFoundException("salida", partnerId));
    }

    @Transactional
    public OutgoingConfigResponse upsertOutgoing(UUID partnerId, OutgoingConfigRequest r) {
        Partner p = partners.active(partnerId);
        String filter = PartnerRules.trim(r.filenameFilter());
        if (r.sendingMode() == SendingMode.FILENAME_FILTER && filter == null) {
            throw new IllegalArgumentException("El filtro de nombre es obligatorio con el modo de envio por filtro");
        }
        ArchiveMode archiveMode = r.archiveMode() == null ? ArchiveMode.MOVE : r.archiveMode();
        String archiveFolder = PartnerRules.trim(r.archiveFolder());
        if (archiveMode == ArchiveMode.MOVE && archiveFolder == null) {
            archiveFolder = storage.folder(p.getAs2Name(), StorageLayout.ARCHIVE);
        }
        OutgoingConfig c = outgoing.findByPartnerId(partnerId).orElseGet(() -> new OutgoingConfig(p));
        c.setLocalFolder(r.localFolder().strip());
        c.setSendingMode(r.sendingMode());
        c.setFilenameFilter(r.sendingMode() == SendingMode.FILENAME_FILTER ? filter : null);
        c.setEncryptionAlgorithm(r.encryptionAlgorithm() != null ? r.encryptionAlgorithm() : p.getEncryptionAlgorithm());
        c.setSigningAlgorithm(r.signingAlgorithm() != null ? r.signingAlgorithm() : p.getSigningAlgorithm());
        c.setCompression(r.compression() != null ? r.compression() : p.isCompressionEnabled());
        c.setMimeType(r.mimeType() == null ? MimeType.APPLICATION_EDI_X12 : r.mimeType());
        c.setArchiveMode(archiveMode);
        c.setArchiveFolder(archiveMode == ArchiveMode.MOVE ? archiveFolder : null);
        c = outgoing.saveAndFlush(c);
        publish("outgoing-config:UPSERT", partnerId, "Configuracion de salida guardada");
        return OutgoingConfigResponse.of(c);
    }

    /** Valores por defecto: envio de todo lo que aparezca en Outgoing y archivo en Archive. */
    public OutgoingConfigRequest defaultOutgoing(Partner p) {
        return new OutgoingConfigRequest(storage.folder(p.getAs2Name(), StorageLayout.OUTGOING),
                SendingMode.REGULAR, null, p.getEncryptionAlgorithm(), p.getSigningAlgorithm(),
                p.isCompressionEnabled(), MimeType.APPLICATION_EDI_X12, ArchiveMode.MOVE,
                storage.folder(p.getAs2Name(), StorageLayout.ARCHIVE));
    }

    // ------------------------------------------------------------------ entrada

    @Transactional(readOnly = true)
    public IncomingConfigResponse getIncoming(UUID partnerId) {
        partners.any(partnerId);
        return incoming.findByPartnerId(partnerId).map(IncomingConfigResponse::of)
                .orElseThrow(() -> new ConfigNotFoundException("entrada", partnerId));
    }

    @Transactional
    public IncomingConfigResponse upsertIncoming(UUID partnerId, IncomingConfigRequest r) {
        Partner p = partners.active(partnerId);
        IncomingConfig c = incoming.findByPartnerId(partnerId).orElseGet(() -> new IncomingConfig(p));
        c.setIncomingFolder(r.incomingFolder().strip());
        c.setParserProfile(PartnerRules.trim(r.parserProfile()));
        c = incoming.saveAndFlush(c);
        publish("incoming-config:UPSERT", partnerId, "Configuracion de entrada guardada");
        return IncomingConfigResponse.of(c);
    }

    public IncomingConfigRequest defaultIncoming(Partner p) {
        return new IncomingConfigRequest(storage.folder(p.getAs2Name(), StorageLayout.INCOMING), null);
    }

    // ------------------------------------------------------------------ MDN

    @Transactional(readOnly = true)
    public MdnConfigResponse getMdn(UUID partnerId) {
        partners.any(partnerId);
        return mdn.findByPartnerId(partnerId).map(MdnConfigResponse::of)
                .orElseThrow(() -> new ConfigNotFoundException("MDN", partnerId));
    }

    @Transactional
    public MdnConfigResponse upsertMdn(UUID partnerId, MdnConfigRequest r) {
        Partner p = partners.active(partnerId);
        String url = PartnerRules.mdnUrl(r.deliveryMode(), r.asyncMdnUrl(), "La URL del MDN asincrono");
        MdnConfig c = mdn.findByPartnerId(partnerId).orElseGet(() -> new MdnConfig(p));
        c.setLocalFolder(r.localFolder().strip());
        c.setReceiptType(r.receiptType());
        c.setDeliveryMode(r.deliveryMode());
        c.setAsyncMdnUrl(url);
        c = mdn.saveAndFlush(c);
        publish("mdn-config:UPSERT", partnerId, "Configuracion de MDN guardada");
        return MdnConfigResponse.of(c);
    }

    /** Valores por defecto tomados de lo que el socio exige: firmado o no, sincrono o asincrono. */
    public MdnConfigRequest defaultMdn(Partner p) {
        return new MdnConfigRequest(storage.folder(p.getAs2Name(), StorageLayout.MDN),
                p.isRequestSignedMdn() ? MdnReceiptType.SIGNED : MdnReceiptType.UNSIGNED,
                p.getMdnMode(), p.getMdnMode() == MdnMode.ASYNC ? p.getMdnReceiptUrl() : null);
    }

    private void publish(String action, UUID partnerId, String detail) {
        audit.publish(new AuditEvent(action, partnerId, RequestIdentity.currentUser(), true, detail));
    }
}
