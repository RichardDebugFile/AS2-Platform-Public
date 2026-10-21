package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.partners.domain.MdnMode;
import edi.partners.domain.Partner;
import edi.partners.domain.TransportType;
import edi.partners.dto.PartnerRequest;
import edi.partners.dto.PartnerResponse;
import edi.partners.error.DuplicateAs2NameException;
import edi.partners.error.PartnerNotFoundException;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.folders.PartnerFolderException;
import edi.partners.svc.folders.PartnerFolderProvisioner;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class PartnerServiceTest {

    private PartnerRepository repo;
    private AuditLogPublisher audit;
    private PartnerFolderProvisioner folders;
    private CryptoService crypto;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        repo = mock(PartnerRepository.class);
        audit = mock(AuditLogPublisher.class);
        folders = mock(PartnerFolderProvisioner.class);
        crypto = new CryptoService(Base64.getEncoder().encodeToString(new byte[32]));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PartnerService(repo, crypto, audit, folders);
    }

    private Partner stored(String as2Name, boolean active) {
        Partner p = new Partner();
        p.setAs2Name(as2Name);
        p.setServerAddress("as2.socio.example");
        p.setPort(443);
        p.setTransportType(TransportType.HTTPS);
        p.setActive(active);
        when(repo.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private AuditEvent lastAudit() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).publish(c.capture());
        return c.getValue();
    }

    @Test
    void alta_creaActivoConValoresPorDefecto_creaCarpetasYPublicaAuditoria() {
        Partner p = service.create(Fixtures.request("  SOCIO-A "));

        assertThat(p.getAs2Name()).isEqualTo("SOCIO-A");
        assertThat(p.isActive()).isTrue();
        assertThat(p.getPath()).isEqualTo("/as2");
        assertThat(p.getMdnMode()).isEqualTo(MdnMode.SYNC);
        assertThat(p.isRequestSignedMdn()).isTrue();
        assertThat(p.endpointUrl()).isEqualTo("https://as2.socio.example:443/as2");
        InOrder order = inOrder(repo, folders, audit);
        order.verify(repo).saveAndFlush(p);
        order.verify(folders).provision(p);
        order.verify(audit).publish(any());
        assertThat(lastAudit().action()).isEqualTo("partners:CREATE");
        assertThat(lastAudit().actor()).isEqualTo("system");
    }

    @Test
    void alta_identificadorDuplicadoEntreActivos_rechazada() {
        when(repo.existsByAs2NameIgnoreCaseAndActiveTrue("SOCIO-A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(Fixtures.request("SOCIO-A")))
                .isInstanceOf(DuplicateAs2NameException.class)
                .hasMessageContaining("SOCIO-A");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void alta_nombreInvalidoParaCarpeta_rechazadaSinGuardar() {
        assertThatThrownBy(() -> service.create(Fixtures.request("COM1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(Fixtures.request("Socio/Prueba")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void alta_siFallanLasCarpetas_laExcepcionSubeParaRevertirLaTransaccion() {
        org.mockito.Mockito.doThrow(new PartnerFolderException("disco lleno")).when(folders).provision(any());

        assertThatThrownBy(() -> service.create(Fixtures.request("SOCIO-A")))
                .isInstanceOf(PartnerFolderException.class);
        verify(audit, never()).publish(any());
    }

    @Test
    void alta_claveBasicaSeGuardaCifradaYNoSaleEnLaRespuesta() {
        Partner p = service.create(Fixtures.request("SOCIO-A", null, null, "usuario", "secreto"));

        assertThat(p.getBasicAuthPasswordEnc()).isNotBlank().doesNotContain("secreto");
        assertThat(crypto.decrypt(p.getBasicAuthPasswordEnc())).isEqualTo("secreto");
        PartnerResponse r = PartnerResponse.of(p);
        assertThat(r.hasBasicAuthPassword()).isTrue();
        assertThat(r.toString()).doesNotContain("secreto");
    }

    @Test
    void alta_mdnAsincronoExigeUrlHttps() {
        assertThatThrownBy(() -> service.create(Fixtures.request("SOCIO-A", MdnMode.ASYNC, null, null, null)))
                .hasMessageContaining("MDN");
        Partner p = service.create(Fixtures.request("SOCIO-B", MdnMode.ASYNC, "https://nosotros.example/mdn", null, null));
        assertThat(p.getMdnReceiptUrl()).isEqualTo("https://nosotros.example/mdn");
    }

    @Test
    void edicion_conservaClaveSiVieneVaciaYLaBorraSiSeQuitaElUsuario() {
        Partner p = stored("SOCIO-A", true);
        p.setBasicAuthUsername("usuario");
        p.setBasicAuthPasswordEnc(crypto.encrypt("secreto"));

        service.update(p.getId(), Fixtures.request("SOCIO-A", null, null, "usuario", ""));
        assertThat(crypto.decrypt(p.getBasicAuthPasswordEnc())).isEqualTo("secreto");

        service.update(p.getId(), Fixtures.request("SOCIO-A", null, null, null, null));
        assertThat(p.getBasicAuthPasswordEnc()).isNull();
        assertThat(lastAudit().action()).isEqualTo("partners:UPDATE");
    }

    @Test
    void edicion_cambiarAUnIdentificadorDeOtroActivo_rechazada() {
        Partner p = stored("SOCIO-A", true);
        when(repo.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot("SOCIO-B", p.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(p.getId(), Fixtures.request("SOCIO-B")))
                .isInstanceOf(DuplicateAs2NameException.class);
    }

    @Test
    void edicion_renombrarRegistraElCambio() {
        Partner p = stored("SOCIO-A", true);

        PartnerResponse r = service.update(p.getId(), Fixtures.request("SOCIO-A2"));

        assertThat(r.as2Name()).isEqualTo("SOCIO-A2");
        assertThat(lastAudit().detail()).contains("SOCIO-A -> SOCIO-A2");
    }

    @Test
    void edicion_deUnSocioDadoDeBaja_noEncontrado() {
        Partner p = stored("SOCIO-A", false);

        assertThatThrownBy(() -> service.update(p.getId(), Fixtures.request("SOCIO-A")))
                .isInstanceOf(PartnerNotFoundException.class);
    }

    @Test
    void bajaLogica_desactivaYAudita() {
        Partner p = stored("SOCIO-A", true);

        service.delete(p.getId());

        assertThat(p.isActive()).isFalse();
        assertThat(lastAudit().action()).isEqualTo("partners:DEACTIVATE");
    }

    @Test
    void reactivar_siOtroActivoUsaElIdentificador_rechazado() {
        Partner p = stored("SOCIO-B", false);
        when(repo.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot("SOCIO-B", p.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.setActive(p.getId(), true)).isInstanceOf(DuplicateAs2NameException.class);
    }

    @Test
    void reactivar_libreYSinCambiosNoAudita() {
        Partner p = stored("SOCIO-B", false);

        assertThat(service.setActive(p.getId(), true).active()).isTrue();
        assertThat(lastAudit().action()).isEqualTo("partners:ACTIVATE");
        org.mockito.Mockito.clearInvocations(audit);

        service.setActive(p.getId(), true);
        verify(audit, never()).publish(any());
    }

    @Test
    void borradoDefinitivo_exigeConfirmarElIdentificador() {
        Partner p = stored("SOCIO-A", false);

        assertThatThrownBy(() -> service.purge(p.getId(), "otro")).hasMessageContaining("SOCIO-A");
        assertThatThrownBy(() -> service.purge(p.getId(), null)).isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).delete(any());

        service.purge(p.getId(), " SOCIO-A ");
        verify(repo).delete(p);
        assertThat(lastAudit().action()).isEqualTo("partners:PURGE");
    }

    @Test
    void consultas() {
        Partner a = stored("SOCIO-A", true);
        when(repo.findAllByActiveOrderByAs2NameAsc(true)).thenReturn(List.of(a));
        when(repo.findByAs2NameIgnoreCaseAndActiveTrue("SOCIO-A")).thenReturn(Optional.of(a));

        assertThat(service.list(true)).extracting(PartnerResponse::as2Name).containsExactly("SOCIO-A");
        assertThat(service.get(a.getId()).id()).isEqualTo(a.getId());
        assertThat(service.findActiveByAs2Name(" SOCIO-A ")).isSameAs(a);
        assertThatThrownBy(() -> service.findActiveByAs2Name("NADIE")).isInstanceOf(PartnerNotFoundException.class);
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(PartnerNotFoundException.class);
        verify(repo).findByAs2NameIgnoreCaseAndActiveTrue(eq("SOCIO-A"));
    }

    @Test
    void edicion_valoresNulosConservanIndicadores() {
        Partner p = stored("SOCIO-A", true);
        p.setEnableIncomingQueue(true);
        p.setRequestSignedMdn(false);
        PartnerRequest r = Fixtures.request("SOCIO-A");

        service.update(p.getId(), r);

        assertThat(p.isEnableIncomingQueue()).isTrue();
        assertThat(p.isRequestSignedMdn()).isFalse();
    }
}
