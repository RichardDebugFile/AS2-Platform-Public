package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.partners.domain.FolderState;
import edi.partners.domain.Partner;
import edi.partners.dto.FolderPrecheckResponse;
import edi.partners.dto.FolderStatusResponse;
import edi.partners.dto.FolderStatusResponse.Health;
import edi.partners.error.FolderPrecheckException;
import edi.partners.error.PartnerStateException;
import edi.partners.repo.PartnerRepository;
import edi.partners.svc.audit.AuditEvent;
import edi.partners.svc.audit.AuditEventTypes;
import edi.partners.svc.audit.AuditLogPublisher;
import edi.partners.svc.folders.FilesystemPartnerFolderProvisioner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Carpetas del socio sobre un sistema de archivos temporal (raiz local y "montaje" del ERP). */
class PartnerFolderServiceTest {

    @TempDir
    Path tmp;

    private Path root;
    private Path mount;
    private PartnerService partners;
    private PartnerRepository repo;
    private AuditLogPublisher audit;
    private FilesystemPartnerFolderProvisioner provisioner;
    private PartnerFolderService service;
    private Partner socio;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("as2_data/partners");
        mount = Files.createDirectories(tmp.resolve("erp"));
        StorageLayout layout = new StorageLayout(root.toString(), mount.toString());
        partners = mock(PartnerService.class);
        repo = mock(PartnerRepository.class);
        audit = mock(AuditLogPublisher.class);
        provisioner = new FilesystemPartnerFolderProvisioner(layout);
        service = new PartnerFolderService(partners, repo, layout, provisioner, audit);
        socio = new Partner();
        socio.setAs2Name("SOCIO-A");
        socio.setServerAddress("as2.socio.example");
        socio.setPort(443);
        socio.setTransportType(edi.partners.domain.TransportType.HTTPS);
        when(partners.active(socio.getId())).thenReturn(socio);
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        provisioner.create("SOCIO-A");
    }

    private Path local(String sub) {
        return root.resolve("SOCIO-A").resolve(sub);
    }

    private AuditEvent lastAudit() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).publish(c.capture());
        return c.getValue();
    }

    // ------------------------------------------------------------------ estado

    @Test
    void estado_conElArbolCompleto_OK() {
        FolderStatusResponse st = service.status(socio.getId());

        assertThat(st.health()).isEqualTo(Health.OK);
        assertThat(st.folders()).extracting(FolderStatusResponse.Folder::name)
                .containsExactly("Archive", "Certs", "Error", "Incoming", "MDN", "Outgoing");
        assertThat(st.problems()).isEmpty();
    }

    @Test
    void estado_indicaLaCarpetaQueFalta_yRepararLaCrea() throws IOException {
        Files.delete(local("Error"));

        FolderStatusResponse st = service.status(socio.getId());
        assertThat(st.health()).isEqualTo(Health.MISSING);
        assertThat(st.problems()).containsExactly("Error: no existe");

        assertThat(service.repair(socio.getId()).health()).isEqualTo(Health.OK);
        assertThat(lastAudit().eventType()).isEqualTo(AuditEventTypes.FOLDERS_REPAIRED);
    }

    // ------------------------------------------------------------------ verificacion previa

    @Test
    void precheck_sinLaCarpetaCompartida_indicaElProblema() {
        FolderPrecheckResponse r = service.precheck(socio.getId(), "SocioA");

        assertThat(r.ok()).isFalse();
        assertThat(r.problems()).singleElement().asString().contains("'SocioA' no existe en el montaje");
    }

    @Test
    void precheck_conElRecursoCompartidoDesmontado_indicaElProblema() throws IOException {
        Files.delete(mount);

        assertThat(service.precheck(socio.getId(), "SocioA").problems())
                .singleElement().asString().contains("no esta montado");
    }

    @Test
    void precheck_nombreConRuta_rechazado() {
        assertThatThrownBy(() -> service.precheck(socio.getId(), "../otro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no una ruta");
        assertThatThrownBy(() -> service.precheck(socio.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void precheck_archivoQueYaExisteEnElDestino_oSubcarpetas_impidenEnlazar() throws IOException {
        Files.createDirectories(mount.resolve("SocioA/Incoming"));
        Files.writeString(mount.resolve("SocioA/Incoming/factura.edi"), "del ERP");
        Files.writeString(local("Incoming/factura.edi"), "local");
        Files.createDirectories(local("Outgoing/lote1"));

        FolderPrecheckResponse r = service.precheck(socio.getId(), "SocioA");

        assertThat(r.ok()).isFalse();
        assertThat(r.problems()).anySatisfy(p -> assertThat(p).contains("Incoming/factura.edi ya existe"))
                .anySatisfy(p -> assertThat(p).contains("Outgoing: contiene subcarpetas [lote1]"));
    }

    @Test
    void precheck_positivo_explicaLoQueHara() throws IOException {
        Files.createDirectories(mount.resolve("SocioA"));
        Files.writeString(local("Outgoing/pedido.edi"), "ISA*00");

        FolderPrecheckResponse r = service.precheck(socio.getId(), "SocioA");

        assertThat(r.ok()).isTrue();
        assertThat(r.target()).isEqualTo(mount.resolve("SocioA").toString());
        assertThat(r.actions()).contains("Outgoing: se moveran 1 archivo(s) a " + mount.resolve("SocioA/Outgoing"))
                .anySatisfy(a -> assertThat(a).startsWith("Incoming: se enlazara con"));
    }

    // ------------------------------------------------------------------ enlace

    @Test
    void enlace_cadaSubcarpetaEsUnEnlace_ningunArchivoSePierde_yQuedaEnLaBitacora() throws IOException {
        Files.createDirectories(mount.resolve("SocioA/Incoming"));
        Files.writeString(mount.resolve("SocioA/Incoming/del-erp.edi"), "ERP");
        Files.writeString(local("Outgoing/pedido.edi"), "ISA*01");
        Files.writeString(local("Archive/viejo.edi"), "ISA*02");

        FolderStatusResponse st = service.link(socio.getId(), "SocioA");

        for (String sub : StorageLayout.TREE) {
            assertThat(Files.isSymbolicLink(local(sub))).as(sub).isTrue();
            assertThat(Files.readSymbolicLink(local(sub))).isEqualTo(mount.resolve("SocioA").resolve(sub));
        }
        assertThat(local("Outgoing/pedido.edi")).hasContent("ISA*01");
        assertThat(mount.resolve("SocioA/Archive/viejo.edi")).hasContent("ISA*02");
        assertThat(mount.resolve("SocioA/Incoming/del-erp.edi")).hasContent("ERP");
        assertThat(socio.getFolderState()).isEqualTo(FolderState.SAMBA_LINKED);
        assertThat(socio.getSharedFolderName()).isEqualTo("SocioA");
        assertThat(st.health()).isEqualTo(Health.OK);
        AuditEvent e = lastAudit();
        assertThat(e.eventType()).isEqualTo(AuditEventTypes.FOLDERS_LINKED);
        assertThat(e.changes()).containsEntry("movedFiles", new AuditEvent.Change(null, 2))
                .containsEntry("folderState", new AuditEvent.Change("LOCAL", "SAMBA_LINKED"));
    }

    @Test
    void enlace_conPrecheckNegativo_noTocaNada() throws IOException {
        Files.writeString(local("Incoming/factura.edi"), "local");

        assertThatThrownBy(() -> service.link(socio.getId(), "SocioA")).isInstanceOf(FolderPrecheckException.class)
                .hasMessageContaining("no existe en el montaje");

        assertThat(local("Incoming/factura.edi")).hasContent("local");
        assertThat(Files.isSymbolicLink(local("Incoming"))).isFalse();
        assertThat(socio.getFolderState()).isEqualTo(FolderState.LOCAL);
    }

    @Test
    void enlace_esReanudable_lasSubcarpetasYaEnlazadasSeSaltan() throws IOException {
        Path remoteIncoming = Files.createDirectories(mount.resolve("SocioA/Incoming"));
        Files.delete(local("Incoming"));
        Files.createSymbolicLink(local("Incoming"), remoteIncoming);

        assertThat(service.precheck(socio.getId(), "SocioA").actions()).contains("Incoming: ya enlazada");
        service.link(socio.getId(), "SocioA");

        assertThat(Files.readSymbolicLink(local("Incoming"))).isEqualTo(remoteIncoming);
        assertThat(Files.isSymbolicLink(local("MDN"))).isTrue();
    }

    @Test
    void conElSocioEnlazado_unNuevoPrecheckLoAdvierte() throws IOException {
        Files.createDirectories(mount.resolve("SocioA"));
        service.link(socio.getId(), "SocioA");

        assertThat(service.precheck(socio.getId(), "SocioA").problems())
                .singleElement().asString().contains("ya esta enlazado");
        assertThatThrownBy(() -> service.repair(socio.getId())).isInstanceOf(PartnerStateException.class);
    }

    @Test
    void montajeCaido_elSocioApareceConError_sinQueElServicioFalle() throws IOException {
        Files.createDirectories(mount.resolve("SocioA"));
        service.link(socio.getId(), "SocioA");
        Files.move(mount.resolve("SocioA"), tmp.resolve("desmontado"));
        when(repo.findAllByActiveOrderByAs2NameAsc(true)).thenReturn(List.of(socio));

        List<FolderStatusResponse> all = service.statuses();

        assertThat(all).singleElement().satisfies(st -> {
            assertThat(st.health()).isEqualTo(Health.ERROR);
            assertThat(st.problems()).anySatisfy(p -> assertThat(p).contains("no esta disponible"));
        });
    }

    // ------------------------------------------------------------------ produccion

    @Test
    void produccion_exigeEnlaceYConfirmacion() {
        assertThatThrownBy(() -> service.production(socio.getId(), true, "otro"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SOCIO-A");
        assertThatThrownBy(() -> service.production(socio.getId(), true, "SOCIO-A"))
                .isInstanceOf(PartnerStateException.class).hasMessageContaining("Primero enlace");
    }

    @Test
    void produccion_reversible_creaLaColaYAlDesactivarLaConserva() throws IOException {
        Files.createDirectories(mount.resolve("SocioA"));
        service.link(socio.getId(), "SocioA");

        var on = service.production(socio.getId(), true, "SOCIO-A");
        assertThat(on.folderState()).isEqualTo(FolderState.PRODUCTION);
        assertThat(on.enableIncomingQueue()).isTrue();
        assertThat(mount.resolve("SocioA/Incoming_queue")).isDirectory();
        assertThat(Files.readSymbolicLink(local("Incoming_queue"))).isEqualTo(mount.resolve("SocioA/Incoming_queue"));
        assertThat(lastAudit().eventType()).isEqualTo(AuditEventTypes.PRODUCTION_ENABLED);
        assertThat(service.status(socio.getId()).folders()).extracting(FolderStatusResponse.Folder::name)
                .contains("Incoming_queue");

        Files.writeString(mount.resolve("SocioA/Incoming_queue/ya-entregado.edi"), "ISA*03");
        var off = service.production(socio.getId(), false, "SOCIO-A");
        assertThat(off.folderState()).isEqualTo(FolderState.SAMBA_LINKED);
        assertThat(off.enableIncomingQueue()).isFalse();
        assertThat(mount.resolve("SocioA/Incoming_queue/ya-entregado.edi")).exists();
        assertThat(lastAudit().eventType()).isEqualTo(AuditEventTypes.PRODUCTION_DISABLED);

        assertThat(service.production(socio.getId(), false, "SOCIO-A").folderState())
                .isEqualTo(FolderState.SAMBA_LINKED); // repetir no cambia nada
    }

    @Test
    void produccion_conCarpetasConProblemas_rechazada() throws IOException {
        Files.createDirectories(mount.resolve("SocioA"));
        service.link(socio.getId(), "SocioA");
        Files.delete(mount.resolve("SocioA/Error"));

        assertThatThrownBy(() -> service.production(socio.getId(), true, "SOCIO-A"))
                .isInstanceOf(PartnerStateException.class).hasMessageContaining("Error: el enlace apunta");
    }
}
