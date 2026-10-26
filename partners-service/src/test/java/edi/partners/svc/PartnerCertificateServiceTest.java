package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.partners.domain.KeyUsage;
import edi.partners.domain.Partner;
import edi.partners.domain.PartnerCertificate;
import edi.partners.domain.TransportType;
import edi.partners.dto.CertificateChangeResponse;
import edi.partners.dto.CertificateResponse;
import edi.partners.dto.CertificateResponse.Validity;
import edi.partners.dto.CertificateUploadResponse;
import edi.partners.dto.CertificateUploadResponse.Outcome;
import edi.partners.error.PartnerStateException;
import edi.partners.repo.PartnerCertificateRepository;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditEventTypes;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.testsupport.MutableClock;
import edi.partners.testsupport.TestCertificates;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** Doble compuerta y retiro de caducados sobre H2 con los repositorios reales (HU-11). */
@DataJpaTest
@Import(edi.partners.config.AuditingConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:certs;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class PartnerCertificateServiceTest {

    @Autowired
    private PartnerRepository partnerRepo;

    @Autowired
    private PartnerCertificateRepository repo;

    private final MutableClock clock = new MutableClock("2026-10-26T12:00:00Z");
    private AuditLogPublisher audit;
    private PartnerCertificateService service;
    private Partner socio;

    @BeforeEach
    void setUp() {
        socio = new Partner();
        socio.setAs2Name("SOCIO-A");
        socio.setServerAddress("as2.socio.example");
        socio.setPort(443);
        socio.setTransportType(TransportType.HTTPS);
        socio = partnerRepo.saveAndFlush(socio);
        PartnerService partners = mock(PartnerService.class);
        when(partners.active(socio.getId())).thenReturn(socio);
        audit = mock(AuditLogPublisher.class);
        service = new PartnerCertificateService(partners, repo, audit, clock);
    }

    private CertificateUploadResponse upload(KeyUsage usage, TestCertificates.Builder cert) {
        return service.upload(socio.getId(), usage, cert.pem(), null);
    }

    private CertificateUploadResponse cifrado(String cn, String from, String to) {
        return upload(KeyUsage.ENCRYPTION, TestCertificates.cert(cn).valid(from, to));
    }

    private PartnerCertificate stored(CertificateUploadResponse r) {
        return repo.findById(r.certificate().id()).orElseThrow();
    }

    private List<String> auditTypes() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).publish(c.capture());
        return c.getAllValues().stream().map(AuditEvent::eventType).toList();
    }

    @Test
    void rotacionSinCorte_elNuevoEsPreferenteYElAnteriorQuedaDeRespaldo() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2026-12-31T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-10-01T00:00:00Z", "2027-12-31T00:00:00Z");

        assertThat(c1.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(c2.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(stored(c2).isActive()).isTrue();
        assertThat(stored(c2).isPreferred()).isTrue();
        assertThat(stored(c1).isActive()).isTrue();
        assertThat(stored(c1).isPreferred()).isFalse();
        assertThat(auditTypes()).containsExactly(AuditEventTypes.CERTIFICATE_UPLOADED, AuditEventTypes.CERTIFICATE_UPLOADED);
    }

    @Test
    void reemplazoAnunciadoQueAunNoEmpieza_seAceptaComoPreferente() {
        CertificateUploadResponse futuro = cifrado("futuro", "2026-11-15T00:00:00Z", "2028-11-15T00:00:00Z");

        assertThat(futuro.certificate().validity()).isEqualTo(Validity.NOT_YET_VALID);
        assertThat(stored(futuro).isPreferred()).isTrue();
    }

    @Test
    void usosDistintos_tienenSuPropioPreferente() {
        CertificateUploadResponse enc = cifrado("enc", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse sig = upload(KeyUsage.SIGNATURE, TestCertificates.cert("sig"));

        assertThat(stored(enc).isPreferred()).isTrue();
        assertThat(stored(sig).isPreferred()).isTrue();
    }

    @Test
    void certificadoDeCaParaCifrar_seRechaza() {
        TestCertificates.Builder ca = TestCertificates.cert("raiz").ca();

        assertThatThrownBy(() -> upload(KeyUsage.ENCRYPTION, ca))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El certificado es de una CA (no entidad final)");
        assertThat(repo.count()).isZero();
        verify(audit, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void caParaTls_seAcepta() {
        assertThat(upload(KeyUsage.SSL, TestCertificates.cert("raiz").ca()).outcome()).isEqualTo(Outcome.CREATED);
    }

    @Test
    void certificadoCaducado_seRechaza() {
        assertThatThrownBy(() -> cifrado("viejo", "2024-01-01T00:00:00Z", "2025-01-01T00:00:00Z"))
                .hasMessage("El certificado ya está expirado");
    }

    @Test
    void certificadoSinUsoDeCifrado_seRechazaParaCifrar_peroSirveParaFirma() {
        TestCertificates.Builder soloFirma = TestCertificates.cert("firma").signatureOnly();

        assertThatThrownBy(() -> upload(KeyUsage.ENCRYPTION, soloFirma)).hasMessageContaining("no permite cifrar");
        assertThat(upload(KeyUsage.SIGNATURE, soloFirma).outcome()).isEqualTo(Outcome.CREATED);
    }

    @Test
    void sinUso_seRechaza() {
        byte[] pem = TestCertificates.cert("x").pem();
        UUID id = socio.getId();

        assertThatThrownBy(() -> service.upload(id, null, pem, null)).hasMessageContaining("Indique el uso");
    }

    @Test
    void resubirElMismoActivo_noCambiaNada() {
        TestCertificates.Builder cert = TestCertificates.cert("c1").valid("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        byte[] pem = cert.pem();
        service.upload(socio.getId(), KeyUsage.ENCRYPTION, pem, null);

        CertificateUploadResponse again = service.upload(socio.getId(), KeyUsage.ENCRYPTION, pem, null);

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_ACTIVE);
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void resubirUnoInactivo_loReactivaComoRespaldoYC2SiguePreferente() {
        byte[] c1Pem = TestCertificates.cert("c1").valid("2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z").pem();
        CertificateUploadResponse c1 = service.upload(socio.getId(), KeyUsage.ENCRYPTION, c1Pem, null);
        CertificateUploadResponse c2 = cifrado("c2", "2026-06-01T00:00:00Z", "2028-01-01T00:00:00Z");
        service.deactivate(socio.getId(), c1.certificate().id());

        CertificateUploadResponse again = service.upload(socio.getId(), KeyUsage.ENCRYPTION, c1Pem, null);

        assertThat(again.outcome()).isEqualTo(Outcome.REACTIVATED);
        assertThat(stored(c1).isActive()).isTrue();
        assertThat(stored(c1).isPreferred()).isFalse();
        assertThat(stored(c2).isPreferred()).isTrue();
        assertThat(auditTypes()).contains(AuditEventTypes.CERTIFICATE_REACTIVATED);
    }

    @Test
    void marcarPreferente_dejaUnoSoloPorUso() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");

        CertificateChangeResponse r = service.prefer(socio.getId(), c1.certificate().id());

        assertThat(r.sameUsage()).filteredOn(CertificateResponse::preferred).extracting(CertificateResponse::id)
                .containsExactly(c1.certificate().id());
        assertThat(stored(c2).isPreferred()).isFalse();
    }

    @Test
    void preferenteSoloSiEstaActivoYVigente() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2026-11-01T00:00:00Z");
        cifrado("c2", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");
        UUID id = socio.getId();
        UUID c1Id = c1.certificate().id();

        clock.set("2026-11-02T00:00:00Z");
        assertThatThrownBy(() -> service.prefer(id, c1Id)).isInstanceOf(PartnerStateException.class)
                .hasMessageContaining("expirado");
        service.deactivate(id, c1Id);
        assertThatThrownBy(() -> service.prefer(id, c1Id)).hasMessageContaining("Active el certificado");
        assertThatThrownBy(() -> service.activate(id, c1Id)).hasMessage("El certificado ya está expirado");
    }

    @Test
    void desactivarElPreferente_promueveElVigenteQueMasTardeVence() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-06-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse c3 = cifrado("c3", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");

        CertificateChangeResponse r = service.deactivate(socio.getId(), c3.certificate().id());

        assertThat(stored(c3).isActive()).isFalse();
        assertThat(stored(c1).isPreferred()).isTrue();
        assertThat(stored(c2).isPreferred()).isFalse();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void desactivarElUnicoActivo_sePermiteConAviso() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");

        CertificateChangeResponse r = service.deactivate(socio.getId(), c1.certificate().id());

        assertThat(r.warnings()).singleElement().asString().contains("los envios a este socio fallaran");
    }

    @Test
    void activar_vuelveComoRespaldo() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");
        service.deactivate(socio.getId(), c1.certificate().id());

        service.activate(socio.getId(), c1.certificate().id());

        assertThat(stored(c1).isActive()).isTrue();
        assertThat(stored(c1).isPreferred()).isFalse();
        assertThat(stored(c2).isPreferred()).isTrue();
    }

    @Test
    void eliminarElPreferente_promueveOtro() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");

        service.delete(socio.getId(), c2.certificate().id());

        assertThat(repo.findById(c2.certificate().id())).isEmpty();
        assertThat(stored(c1).isPreferred()).isTrue();
        assertThat(auditTypes()).contains(AuditEventTypes.CERTIFICATE_DELETED);
    }

    @Test
    void masDeTresDeCifradoActivos_avisa() {
        cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        cifrado("c2", "2026-01-01T00:00:00Z", "2027-02-01T00:00:00Z");
        cifrado("c3", "2026-01-01T00:00:00Z", "2027-03-01T00:00:00Z");

        CertificateUploadResponse c4 = cifrado("c4", "2026-01-01T00:00:00Z", "2027-04-01T00:00:00Z");

        assertThat(c4.warnings()).singleElement().asString().contains("como maximo para 3");
    }

    @Test
    void retiroAutomatico_desactivaElCaducadoYPromueveElVigente() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2026-11-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2027-11-01T00:00:00Z");
        service.prefer(socio.getId(), c1.certificate().id());

        PartnerCertificateService.RetirementResult r = service.retireExpired(Instant.parse("2026-11-02T11:30:00Z"));

        assertThat(r.retired()).isEqualTo(1);
        assertThat(r.kept()).isZero();
        assertThat(stored(c1).isActive()).isFalse();
        assertThat(stored(c2).isPreferred()).isTrue();
        assertThat(auditTypes()).contains(AuditEventTypes.CERTIFICATE_RETIRED);
    }

    @Test
    void unicoCertificadoCaducado_seConservaConAdvertencia() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2026-11-01T00:00:00Z");

        PartnerCertificateService.RetirementResult r = service.retireExpired(Instant.parse("2026-11-02T11:30:00Z"));

        assertThat(r.retired()).isZero();
        assertThat(r.kept()).isEqualTo(1);
        assertThat(stored(c1).isActive()).isTrue();
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).publish(c.capture());
        AuditEvent last = c.getValue();
        assertThat(last.eventType()).isEqualTo(AuditEventTypes.CERTIFICATE_EXPIRED);
        assertThat(last.success()).isFalse();
        assertThat(last.actor()).isEqualTo("system");
    }

    @Test
    void retiro_noTocaLosDeOtroUso() {
        CertificateUploadResponse enc = cifrado("enc", "2026-01-01T00:00:00Z", "2026-11-01T00:00:00Z");
        upload(KeyUsage.SIGNATURE, TestCertificates.cert("sig").valid("2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z"));

        service.retireExpired(Instant.parse("2026-11-02T11:30:00Z"));

        assertThat(stored(enc).isActive()).isTrue();
    }

    @Test
    void vigencia_semaforo() {
        CertificateUploadResponse pronto = cifrado("pronto", "2026-01-01T00:00:00Z", "2026-11-10T00:00:00Z");
        CertificateUploadResponse lejos = cifrado("lejos", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");

        assertThat(pronto.certificate().validity()).isEqualTo(Validity.EXPIRING);
        assertThat(pronto.certificate().daysToExpire()).isEqualTo(14);
        assertThat(lejos.certificate().validity()).isEqualTo(Validity.VALID);
    }

    @Test
    void descripcion_seEditaYQuedaEnLaBitacora() {
        CertificateUploadResponse c1 = cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");

        CertificateResponse r = service.update(socio.getId(), c1.certificate().id(), "  Rotacion de 2026  ");
        service.update(socio.getId(), c1.certificate().id(), "Rotacion de 2026");

        assertThat(r.description()).isEqualTo("Rotacion de 2026");
        assertThat(auditTypes()).containsOnlyOnce(AuditEventTypes.CERTIFICATE_UPDATED);
    }

    @Test
    void motor_recibeLosActivosConElPreferentePrimero() {
        cifrado("c1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        CertificateUploadResponse c2 = cifrado("c2", "2026-01-01T00:00:00Z", "2028-01-01T00:00:00Z");

        var engine = service.activeForEngine(socio.getId(), KeyUsage.ENCRYPTION);

        assertThat(engine).hasSize(2);
        assertThat(engine.get(0).id()).isEqualTo(c2.certificate().id());
        assertThat(engine.get(0).pem()).startsWith("-----BEGIN CERTIFICATE-----");
        assertThat(service.forEngine(c2.certificate().id()).partnerAs2Name()).isEqualTo("SOCIO-A");
    }
}
