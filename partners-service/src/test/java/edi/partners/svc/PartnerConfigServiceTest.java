package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.partners.domain.ArchiveMode;
import edi.partners.domain.EncryptionAlgorithm;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import edi.partners.domain.MimeType;
import edi.partners.domain.Partner;
import edi.partners.domain.SendingMode;
import edi.partners.domain.SigningAlgorithm;
import edi.partners.domain.TransportType;
import edi.partners.dto.IncomingConfigRequest;
import edi.partners.dto.MdnConfigRequest;
import edi.partners.dto.OutgoingConfigRequest;
import edi.partners.dto.OutgoingConfigResponse;
import edi.partners.error.ConfigNotFoundException;
import edi.partners.error.PartnerNotFoundException;
import edi.partners.repo.IncomingConfigRepository;
import edi.partners.repo.MdnConfigRepository;
import edi.partners.repo.OutgoingConfigRepository;
import edi.partners.svc.audit.AuditLogPublisher;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartnerConfigServiceTest {

    private PartnerService partners;
    private OutgoingConfigRepository outgoing;
    private IncomingConfigRepository incoming;
    private MdnConfigRepository mdn;
    private AuditLogPublisher audit;
    private PartnerConfigService service;
    private Partner partner;

    @BeforeEach
    void setUp() {
        partners = mock(PartnerService.class);
        outgoing = mock(OutgoingConfigRepository.class);
        incoming = mock(IncomingConfigRepository.class);
        mdn = mock(MdnConfigRepository.class);
        audit = mock(AuditLogPublisher.class);
        when(outgoing.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(incoming.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mdn.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PartnerConfigService(partners, outgoing, incoming, mdn, new StorageLayout("/data/partners"), audit);

        partner = new Partner();
        partner.setAs2Name("SOCIO-A");
        partner.setServerAddress("as2.socio.example");
        partner.setPort(443);
        partner.setTransportType(TransportType.HTTPS);
        partner.setSigningAlgorithm(SigningAlgorithm.RSA_SHA256);
        partner.setEncryptionAlgorithm(EncryptionAlgorithm.AES256);
        when(partners.active(partner.getId())).thenReturn(partner);
        when(partners.any(partner.getId())).thenReturn(partner);
    }

    private String folder(String sub) {
        return Path.of("/data/partners", "SOCIO-A", sub).toString();
    }

    @Test
    void salidaPorDefecto_tomaAlgoritmosDelSocioYCarpetasDeSuRaiz() {
        OutgoingConfigResponse r = service.upsertOutgoing(partner.getId(), service.defaultOutgoing(partner));

        assertThat(r.localFolder()).isEqualTo(folder("Outgoing"));
        assertThat(r.sendingMode()).isEqualTo(SendingMode.REGULAR);
        assertThat(r.signingAlgorithm()).isEqualTo(SigningAlgorithm.RSA_SHA256);
        assertThat(r.encryptionAlgorithm()).isEqualTo(EncryptionAlgorithm.AES256);
        assertThat(r.archiveMode()).isEqualTo(ArchiveMode.MOVE);
        assertThat(r.archiveFolder()).isEqualTo(folder("Archive"));
        assertThat(r.mimeType()).isEqualTo(MimeType.APPLICATION_EDI_X12);
        verify(audit).publish(any());
    }

    @Test
    void salida_filtroObligatorioEnModoFiltro_yArchivoPorDefecto() {
        UUID id = partner.getId();
        assertThatThrownBy(() -> service.upsertOutgoing(id, new OutgoingConfigRequest(
                "/out", SendingMode.FILENAME_FILTER, " ", null, null, null, null, null, null)))
                .hasMessageContaining("filtro");

        OutgoingConfigResponse r = service.upsertOutgoing(id, new OutgoingConfigRequest(
                "/out", SendingMode.FILENAME_FILTER, "*.x12", null, null, true, MimeType.TEXT_PLAIN, null, null));
        assertThat(r.filenameFilter()).isEqualTo("*.x12");
        assertThat(r.archiveFolder()).isEqualTo(folder("Archive"));
        assertThat(r.compression()).isTrue();

        r = service.upsertOutgoing(id, new OutgoingConfigRequest(
                "/out", SendingMode.NONE, "*.x12", null, null, null, null, ArchiveMode.DELETE, "/arch"));
        assertThat(r.filenameFilter()).as("solo se guarda con modo filtro").isNull();
        assertThat(r.archiveFolder()).as("solo se guarda al mover").isNull();
    }

    @Test
    void entrada_seGuardaRecortada() {
        var r = service.upsertIncoming(partner.getId(), new IncomingConfigRequest(" /in ", "  "));

        assertThat(r.incomingFolder()).isEqualTo("/in");
        assertThat(r.parserProfile()).isNull();
        assertThat(service.defaultIncoming(partner).incomingFolder()).isEqualTo(folder("Incoming"));
    }

    @Test
    void mdn_asincronoExigeUrlHttps_ySincronoLaDescarta() {
        UUID id = partner.getId();
        assertThatThrownBy(() -> service.upsertMdn(id,
                new MdnConfigRequest("/mdn", MdnReceiptType.SIGNED, MdnMode.ASYNC, "http://x.example")))
                .hasMessageContaining("https");

        var r = service.upsertMdn(id, new MdnConfigRequest("/mdn", MdnReceiptType.SIGNED, MdnMode.SYNC,
                "https://x.example/mdn"));
        assertThat(r.asyncMdnUrl()).isNull();
    }

    @Test
    void mdnPorDefecto_reflejaLoQueExigeElSocio() {
        partner.setRequestSignedMdn(false);
        partner.setMdnMode(MdnMode.ASYNC);
        partner.setMdnReceiptUrl("https://nosotros.example/mdn");

        MdnConfigRequest d = service.defaultMdn(partner);

        assertThat(d.localFolder()).isEqualTo(folder("MDN"));
        assertThat(d.receiptType()).isEqualTo(MdnReceiptType.UNSIGNED);
        assertThat(d.deliveryMode()).isEqualTo(MdnMode.ASYNC);
        assertThat(d.asyncMdnUrl()).isEqualTo("https://nosotros.example/mdn");
    }

    @Test
    void consultas_sinConfiguracion_noEncontrada() {
        UUID id = partner.getId();
        when(outgoing.findByPartnerId(id)).thenReturn(Optional.empty());
        when(incoming.findByPartnerId(id)).thenReturn(Optional.empty());
        when(mdn.findByPartnerId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOutgoing(id)).isInstanceOf(ConfigNotFoundException.class);
        assertThatThrownBy(() -> service.getIncoming(id)).isInstanceOf(ConfigNotFoundException.class);
        assertThatThrownBy(() -> service.getMdn(id)).isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("MDN");
    }

    @Test
    void socioInexistente_noEncontrado() {
        UUID otro = UUID.randomUUID();
        when(partners.active(otro)).thenThrow(new PartnerNotFoundException(otro));

        assertThatThrownBy(() -> service.upsertIncoming(otro, new IncomingConfigRequest("/in", null)))
                .isInstanceOf(PartnerNotFoundException.class);
    }
}
