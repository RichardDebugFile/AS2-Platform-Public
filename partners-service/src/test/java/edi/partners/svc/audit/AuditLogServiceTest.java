package edi.partners.svc.audit;

import static org.assertj.core.api.Assertions.assertThat;

import edi.partners.domain.AuditStatus;
import edi.partners.domain.Partner;
import edi.partners.dto.AuditLogQuery;
import edi.partners.dto.AuditLogRequest;
import edi.partners.dto.AuditLogResponse;
import edi.partners.repo.AuditLogRepository;
import edi.partners.repo.PartnerRepository;
import edi.partners.domain.TransportType;
import edi.partners.testsupport.TickingClock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Registro y busqueda de la bitacora sobre H2 con el publicador real. */
@DataJpaTest
@Import(edi.partners.config.AuditingConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class AuditLogServiceTest {

    @Autowired
    private AuditLogRepository repo;

    @Autowired
    private PartnerRepository partners;

    private JpaAuditLogPublisher publisher;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        publisher = new JpaAuditLogPublisher(repo, new TickingClock());
        service = new AuditLogService(repo, partners, publisher);
    }

    private static AuditLogQuery none() {
        return new AuditLogQuery(null, null, null, null, null, null, null, null);
    }

    private static AuditEvent event(String type, UUID partner, String as2Name, String actor, boolean ok, String msgId) {
        return new AuditEvent(type, partner, as2Name, msgId, actor, ok, "detalle", Map.of(), "as2-adapter");
    }

    @Test
    void publicar_guardaElEventoConSusCambiosEnJson() {
        UUID id = UUID.randomUUID();
        publisher.publish(AuditEvent.of(AuditEventTypes.PARTNER_UPDATED, id, "SOCIO-A", "operador1", "Socio actualizado",
                Map.of("port", new AuditEvent.Change(443, 8443))));

        AuditLogResponse r = service.search(none(), 0, 20).getContent().getFirst();
        assertThat(r.eventType()).isEqualTo("PARTNER_UPDATED");
        assertThat(r.actor()).isEqualTo("operador1");
        assertThat(r.source()).isEqualTo("partners-service");
        assertThat(r.partnerId()).isEqualTo(id);
        assertThat(r.changes()).containsEntry("port", Map.of("from", 443, "to", 8443));
    }

    @Test
    void filtros_porSocioTipoEstadoUsuarioYFechas() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        publisher.publish(event("DOCUMENT_SENT", a, "SOCIO-A", "system", true, null));      // 12:01
        publisher.publish(event("DOCUMENT_SENT", b, "SOCIO-B", "system", false, null));     // 12:02
        publisher.publish(event("PARTNER_UPDATED", a, "SOCIO-A", "Operador1", true, null)); // 12:03

        assertThat(service.search(new AuditLogQuery(a, null, null, null, null, null, null, null), 0, 20))
                .hasSize(2);
        assertThat(service.search(new AuditLogQuery(null, "socio-b", null, null, null, null, null, null), 0, 20))
                .extracting(AuditLogResponse::partnerId).containsExactly(b);
        assertThat(service.search(new AuditLogQuery(null, null, "document_sent", null, null, null, null, null), 0, 20))
                .hasSize(2);
        assertThat(service.search(new AuditLogQuery(null, null, null, AuditStatus.FAILURE, null, null, null, null),
                0, 20)).extracting(AuditLogResponse::partnerAs2Name).containsExactly("SOCIO-B");
        assertThat(service.search(new AuditLogQuery(null, null, null, null, "operador1", null, null, null), 0, 20))
                .extracting(AuditLogResponse::eventType).containsExactly("PARTNER_UPDATED");
        assertThat(service.search(new AuditLogQuery(null, null, null, null, null, null,
                Instant.parse("2026-10-20T12:02:00Z"), Instant.parse("2026-10-20T12:03:00Z")), 0, 20))
                .extracting(AuditLogResponse::partnerAs2Name).containsExactly("SOCIO-B");
    }

    @Test
    void sinFiltros_delMasRecienteAlMasAntiguo_yPaginado() {
        for (int i = 0; i < 5; i++) {
            publisher.publish(event("DOCUMENT_SENT", null, "SOCIO-" + i, "system", true, null));
        }

        var page = service.search(none(), 0, 2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).extracting(AuditLogResponse::partnerAs2Name).containsExactly("SOCIO-4", "SOCIO-3");
        assertThat(service.search(none(), 0, 100_000).getSize()).isEqualTo(AuditLogService.MAX_PAGE);
    }

    @Test
    void porMessageId_losEventosDelIntercambioEnOrdenCronologico() {
        String msg = "<20261020.1@as2.example.com>";
        publisher.publish(event("DOCUMENT_SENT", null, "SOCIO-A", "system", true, msg));
        publisher.publish(event("DOCUMENT_SENT", null, "SOCIO-A", "system", true, "<otro@as2.example.com>"));
        publisher.publish(event("MDN_RECEIVED", null, "SOCIO-A", "system", true, msg));
        publisher.publish(event("MIC_VERIFIED", null, "SOCIO-A", "system", true, msg));

        var q = new AuditLogQuery(null, null, null, null, null, msg, null, null);
        assertThat(service.search(q, 0, 20)).extracting(AuditLogResponse::eventType)
                .containsExactly("DOCUMENT_SENT", "MDN_RECEIVED", "MIC_VERIFIED");
        assertThat(service.export(q)).hasSize(3);
    }

    @Test
    void eventoDeOtroServicio_completaElSocioPorSuIdentificador_yUsuarioSystem() {
        Partner nuevo = new Partner();
        nuevo.setAs2Name("SOCIO-INT");
        nuevo.setServerAddress("as2.socio-int.example");
        nuevo.setPort(443);
        nuevo.setTransportType(TransportType.HTTPS);
        Partner p = partners.saveAndFlush(nuevo);

        AuditLogResponse r = service.record(new AuditLogRequest("DOCUMENT_RECEIVED", AuditStatus.SUCCESS, "as2-adapter",
                null, null, "socio-int", "<m1@as2.example.com>", "Factura recibida",
                Map.of("bytes", new AuditLogRequest.ChangeValue(null, 1200))));

        assertThat(r.partnerId()).isEqualTo(p.getId());
        assertThat(r.actor()).isEqualTo("system");
        assertThat(r.status()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(r.changes()).containsKey("bytes");
        assertThat(service.eventTypes()).containsExactly("DOCUMENT_RECEIVED");
    }

    @Test
    void detalleMuyLargo_seRecorta() {
        publisher.publish(new AuditEvent("DOCUMENT_SENT", null, null, null, "system", true, "x".repeat(5000), null,
                "as2-adapter"));

        assertThat(service.search(none(), 0, 1).getContent().getFirst().detail())
                .hasSize(JpaAuditLogPublisher.MAX_DETAIL);
    }
}
