package edi.partners.svc.audit;

import static org.assertj.core.api.Assertions.assertThat;

import edi.partners.domain.MdnMode;
import edi.partners.svc.audit.AuditEvent.Change;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangesTest {

    record Foto(UUID id, String name, int port, MdnMode mode, String note, Instant updatedAt) {
    }

    @Test
    void soloLosCamposQueCambian_conEnumeradosPorNombre_ySinCamposTecnicos() {
        UUID id = UUID.randomUUID();
        var antes = new Foto(id, "SOCIO-A", 443, MdnMode.SYNC, "igual", Instant.EPOCH);
        var despues = new Foto(UUID.randomUUID(), "SOCIO-A", 8443, MdnMode.ASYNC, "igual", Instant.now());

        assertThat(Changes.between(antes, despues))
                .containsExactly(java.util.Map.entry("port", new Change(443, 8443)),
                        java.util.Map.entry("mode", new Change("SYNC", "ASYNC")));
    }

    @Test
    void enElAlta_listaLosCamposConValor() {
        var nueva = new Foto(UUID.randomUUID(), "SOCIO-A", 443, null, null, Instant.now());

        assertThat(Changes.between(null, nueva)).containsOnlyKeys("name", "port");
    }

    @Test
    void sinCambios_mapaVacio() {
        var f = new Foto(UUID.randomUUID(), "SOCIO-A", 443, MdnMode.SYNC, null, Instant.EPOCH);

        assertThat(Changes.between(f, f)).isEmpty();
    }
}
