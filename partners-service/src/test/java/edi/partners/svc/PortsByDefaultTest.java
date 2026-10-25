package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThatCode;

import edi.partners.domain.Partner;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.LoggingAuditLogPublisher;
import edi.partners.svc.folders.NoOpPartnerFolderProvisioner;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Implementaciones provisionales hasta HU-09 (carpetas) y HU-10 (bitacora): no deben fallar. */
class PortsByDefaultTest {

    @Test
    void publicadorEnLog_yCarpetasSinAlmacenamiento_noFallan() {
        Partner p = new Partner();
        p.setAs2Name("SOCIO-A");

        assertThatCode(() -> new LoggingAuditLogPublisher()
                .publish(new AuditEvent("partners:CREATE", UUID.randomUUID(), "admin", true, "Socio creado")))
                .doesNotThrowAnyException();
        assertThatCode(() -> new NoOpPartnerFolderProvisioner().provision(p)).doesNotThrowAnyException();
    }
}
