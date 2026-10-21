package edi.partners.svc.folders;

import edi.partners.domain.Partner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sin almacenamiento configurado todavia (HU-09): solo deja constancia en el log. */
@Component
public class NoOpPartnerFolderProvisioner implements PartnerFolderProvisioner {

    private static final Logger log = LoggerFactory.getLogger(NoOpPartnerFolderProvisioner.class);

    @Override
    public void provision(Partner partner) {
        log.debug("Carpetas del socio {} pendientes de HU-09", partner.getAs2Name());
    }
}
