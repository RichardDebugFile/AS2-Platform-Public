package edi.partners.svc;

import edi.partners.domain.Partner;
import edi.partners.dto.CompletePartnerRequest;
import edi.partners.dto.CompletePartnerResponse;
import edi.partners.dto.PartnerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta en un paso: socio, carpetas y sus tres configuraciones en una sola transaccion. Si cualquier
 * paso falla (identificador repetido, regla de una configuracion, carpetas) no queda nada en la base.
 */
@Service
public class CompletePartnerService {

    private final PartnerService partners;
    private final PartnerConfigService configs;

    public CompletePartnerService(PartnerService partners, PartnerConfigService configs) {
        this.partners = partners;
        this.configs = configs;
    }

    @Transactional
    public CompletePartnerResponse create(CompletePartnerRequest r) {
        Partner p = partners.create(r.partner());
        var out = configs.upsertOutgoing(p.getId(), r.outgoing() != null ? r.outgoing() : configs.defaultOutgoing(p));
        var in = configs.upsertIncoming(p.getId(), r.incoming() != null ? r.incoming() : configs.defaultIncoming(p));
        var mdn = configs.upsertMdn(p.getId(), r.mdn() != null ? r.mdn() : configs.defaultMdn(p));
        return new CompletePartnerResponse(PartnerResponse.of(p), out, in, mdn);
    }
}
