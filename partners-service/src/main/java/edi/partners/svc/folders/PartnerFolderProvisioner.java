package edi.partners.svc.folders;

import edi.partners.domain.Partner;

/**
 * Crea las carpetas del socio en el almacenamiento. El alta completa la llama dentro de su
 * transaccion: si lanza {@link PartnerFolderException}, no queda nada del socio en la base.
 * La implementacion real llega con HU-09.
 */
public interface PartnerFolderProvisioner {

    void provision(Partner partner);
}
