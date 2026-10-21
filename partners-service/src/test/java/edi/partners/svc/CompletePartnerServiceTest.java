package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import edi.partners.config.AuditingConfig;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import edi.partners.dto.CompletePartnerRequest;
import edi.partners.dto.CompletePartnerResponse;
import edi.partners.dto.MdnConfigRequest;
import edi.partners.error.DuplicateAs2NameException;
import edi.partners.repo.IncomingConfigRepository;
import edi.partners.repo.MdnConfigRepository;
import edi.partners.repo.OutgoingConfigRepository;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.folders.PartnerFolderException;
import edi.partners.svc.folders.PartnerFolderProvisioner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta completa con transacciones reales (la prueba no abre la suya): o queda el socio con sus
 * tres configuraciones, o no queda nada.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuditingConfig.class, CompletePartnerService.class, PartnerService.class, PartnerConfigService.class,
        StorageLayout.class, CryptoService.class})
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:complete;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "partners.storage.root=/data/partners"
})
class CompletePartnerServiceTest {

    @Autowired
    private CompletePartnerService service;

    @Autowired
    private PartnerRepository partners;

    @Autowired
    private OutgoingConfigRepository outgoing;

    @Autowired
    private IncomingConfigRepository incoming;

    @Autowired
    private MdnConfigRepository mdn;

    @MockitoBean
    private PartnerFolderProvisioner folders;

    @MockitoBean
    private AuditLogPublisher audit;

    @AfterEach
    void clean() {
        outgoing.deleteAll();
        incoming.deleteAll();
        mdn.deleteAll();
        partners.deleteAll();
    }

    @Test
    void altaCompleta_dejaElSocioActivoConSusTresConfiguraciones() {
        CompletePartnerResponse r = service.create(
                new CompletePartnerRequest(Fixtures.request("SOCIO-A"), null, null, null));

        assertThat(r.partner().active()).isTrue();
        assertThat(r.partner().createdBy()).isEqualTo("system");
        assertThat(r.outgoing().localFolder()).endsWith("Outgoing");
        assertThat(r.incoming().incomingFolder()).endsWith("Incoming");
        assertThat(r.mdn().receiptType()).isEqualTo(MdnReceiptType.SIGNED);
        assertThat(partners.count()).isEqualTo(1);
        assertThat(outgoing.findByPartnerId(r.partner().id())).isPresent();
        assertThat(incoming.findByPartnerId(r.partner().id())).isPresent();
        assertThat(mdn.findByPartnerId(r.partner().id())).isPresent();
        verify(audit, org.mockito.Mockito.atLeast(4)).publish(any());
    }

    @Test
    void siFallanLasCarpetas_noQuedaNiElSocioNiSusConfiguraciones() {
        doThrow(new PartnerFolderException("sin permisos")).when(folders).provision(any());

        assertThatThrownBy(() -> service.create(
                new CompletePartnerRequest(Fixtures.request("SOCIO-B"), null, null, null)))
                .isInstanceOf(PartnerFolderException.class);

        assertThat(partners.count()).isZero();
        assertThat(outgoing.count()).isZero();
        assertThat(incoming.count()).isZero();
        assertThat(mdn.count()).isZero();
    }

    @Test
    void siFallaUnaConfiguracion_tampocoQuedaElSocio() {
        MdnConfigRequest mdnInvalido = new MdnConfigRequest("/mdn", MdnReceiptType.SIGNED, MdnMode.ASYNC, null);

        assertThatThrownBy(() -> service.create(
                new CompletePartnerRequest(Fixtures.request("SOCIO-C"), null, null, mdnInvalido)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(partners.count()).isZero();
        assertThat(outgoing.count()).isZero();
    }

    @Test
    void identificadorDeUnSocioDadoDeBaja_seReutiliza_peroNoElDeUnoActivo() {
        var primero = service.create(new CompletePartnerRequest(Fixtures.request("SOCIO-D"), null, null, null));
        var duplicado = new CompletePartnerRequest(Fixtures.request("socio-d"), null, null, null);

        assertThatThrownBy(() -> service.create(duplicado)).isInstanceOf(DuplicateAs2NameException.class);

        var p = partners.findById(primero.partner().id()).orElseThrow();
        p.setActive(false);
        partners.save(p);
        assertThat(service.create(new CompletePartnerRequest(Fixtures.request("SOCIO-D"), null, null, null))
                .partner().active()).isTrue();
    }
}
