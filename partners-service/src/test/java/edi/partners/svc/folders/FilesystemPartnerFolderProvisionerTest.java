package edi.partners.svc.folders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.partners.domain.Partner;
import edi.partners.svc.StorageLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FilesystemPartnerFolderProvisionerTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clearTx() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private FilesystemPartnerFolderProvisioner provisioner(Path root) {
        return new FilesystemPartnerFolderProvisioner(new StorageLayout(root.toString(), tmp.resolve("erp").toString()));
    }

    private static Partner partner(String as2Name) {
        Partner p = new Partner();
        p.setAs2Name(as2Name);
        return p;
    }

    /** Simula el final de la transaccion del alta con el estado dado. */
    private static void finishTx(int status) {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(status);
        }
    }

    @Test
    void alRegistrar_creaElArbolEstandar() {
        Path root = tmp.resolve("partners");
        provisioner(root).provision(partner("SOCIO-A"));

        for (String sub : new String[] {"Archive", "Certs", "Error", "Incoming", "MDN", "Outgoing"}) {
            assertThat(root.resolve("SOCIO-A").resolve(sub)).isDirectory();
        }
    }

    @Test
    void siElAltaSeDeshace_noQuedanCarpetasSueltas() {
        Path root = tmp.resolve("partners");
        TransactionSynchronizationManager.initSynchronization();

        provisioner(root).provision(partner("SOCIO-B"));
        assertThat(root.resolve("SOCIO-B/Incoming")).isDirectory();
        finishTx(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(root.resolve("SOCIO-B")).doesNotExist();
        assertThat(root).isDirectory(); // la raiz comun se conserva
    }

    @Test
    void siElAltaSeConfirma_lasCarpetasQuedan() {
        Path root = tmp.resolve("partners");
        TransactionSynchronizationManager.initSynchronization();

        provisioner(root).provision(partner("SOCIO-C"));
        finishTx(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(root.resolve("SOCIO-C/Outgoing")).isDirectory();
    }

    @Test
    void lasCarpetasQueYaExistian_seConservanAunqueElAltaSeDeshaga() throws IOException {
        Path root = tmp.resolve("partners");
        Files.createDirectories(root.resolve("SOCIO-D/Incoming"));
        Files.writeString(root.resolve("SOCIO-D/Incoming/factura.edi"), "ISA*00");
        TransactionSynchronizationManager.initSynchronization();

        provisioner(root).provision(partner("SOCIO-D"));
        finishTx(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(root.resolve("SOCIO-D/Incoming/factura.edi")).hasContent("ISA*00");
        assertThat(root.resolve("SOCIO-D/Outgoing")).doesNotExist(); // la creada en esta llamada se quita
    }

    @Test
    void deshacer_nuncaBorraUnaCarpetaConContenido() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("x/Incoming"));
        Files.writeString(dir.resolve("llego-mientras.edi"), "ISA*00");

        FilesystemPartnerFolderProvisioner.undo(java.util.List.of(tmp.resolve("x"), dir));

        assertThat(dir.resolve("llego-mientras.edi")).exists();
    }

    @Test
    void siNoSePuedenCrear_fallaConMensajeYNoDejaNada() throws IOException {
        Path root = tmp.resolve("partners");
        Files.createDirectories(root.resolve("SOCIO-E"));
        Files.writeString(root.resolve("SOCIO-E/Outgoing"), "un archivo donde va una carpeta");

        assertThatThrownBy(() -> provisioner(root).provision(partner("SOCIO-E")))
                .isInstanceOf(PartnerFolderException.class)
                .hasMessageContaining("No se pudieron crear las carpetas de SOCIO-E");
        assertThat(root.resolve("SOCIO-E/Archive")).doesNotExist();
        assertThat(root.resolve("SOCIO-E/Outgoing")).isRegularFile();
    }
}
