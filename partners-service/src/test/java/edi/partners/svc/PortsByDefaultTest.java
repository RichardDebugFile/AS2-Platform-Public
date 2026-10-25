package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThatCode;

import edi.partners.domain.Partner;
import edi.partners.svc.folders.NoOpPartnerFolderProvisioner;
import org.junit.jupiter.api.Test;

/** Implementacion provisional hasta HU-09 (carpetas): no debe fallar. */
class PortsByDefaultTest {

    @Test
    void carpetasSinAlmacenamiento_noFallan() {
        Partner p = new Partner();
        p.setAs2Name("SOCIO-A");

        assertThatCode(() -> new NoOpPartnerFolderProvisioner().provision(p)).doesNotThrowAnyException();
    }
}
