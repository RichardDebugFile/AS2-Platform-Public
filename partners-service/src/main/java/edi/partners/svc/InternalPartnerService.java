package edi.partners.svc;

import edi.partners.domain.Partner;
import edi.partners.domain.SendingMode;
import edi.partners.dto.FolderStatusResponse;
import edi.partners.dto.IncomingConfigResponse;
import edi.partners.dto.InternalPartnerView;
import edi.partners.dto.MdnConfigResponse;
import edi.partners.dto.OutgoingConfigResponse;
import edi.partners.dto.PartnerResponse;
import edi.partners.repo.IncomingConfigRepository;
import edi.partners.repo.MdnConfigRepository;
import edi.partners.repo.OutgoingConfigRepository;
import edi.partners.repo.PartnerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas de los demas servicios (motor AS2) por la red interna. */
@Service
public class InternalPartnerService {

    private final PartnerService partners;
    private final PartnerRepository partnerRepo;
    private final OutgoingConfigRepository outgoing;
    private final IncomingConfigRepository incoming;
    private final MdnConfigRepository mdn;
    private final PartnerFolderService folders;

    public InternalPartnerService(PartnerService partners, PartnerRepository partnerRepo,
                                  OutgoingConfigRepository outgoing, IncomingConfigRepository incoming,
                                  MdnConfigRepository mdn, PartnerFolderService folders) {
        this.partners = partners;
        this.partnerRepo = partnerRepo;
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.mdn = mdn;
        this.folders = folders;
    }

    /** Socio activo por su identificador AS2 (cabecera AS2-From) con su configuracion vigente. */
    @Transactional(readOnly = true)
    public InternalPartnerView byAs2Name(String as2Name) {
        Partner p = partners.findActiveByAs2Name(as2Name);
        return new InternalPartnerView(PartnerResponse.of(p),
                outgoing.findByPartnerId(p.getId()).map(OutgoingConfigResponse::of).orElse(null),
                incoming.findByPartnerId(p.getId()).map(IncomingConfigResponse::of).orElse(null),
                mdn.findByPartnerId(p.getId()).map(MdnConfigResponse::of).orElse(null));
    }

    /**
     * Socios activos cuya carpeta de salida se vigila: tienen envio automatico configurado y sus
     * carpetas estan sanas (con el recurso compartido caido, el socio sale de la lista hasta que vuelva).
     */
    @Transactional(readOnly = true)
    public List<String> fileWatcherEnabled() {
        return partnerRepo.findAllByActiveOrderByAs2NameAsc(true).stream()
                .filter(p -> outgoing.existsByPartnerIdAndSendingModeNot(p.getId(), SendingMode.NONE))
                .filter(p -> folders.status(p).health() == FolderStatusResponse.Health.OK)
                .map(Partner::getAs2Name)
                .toList();
    }
}
