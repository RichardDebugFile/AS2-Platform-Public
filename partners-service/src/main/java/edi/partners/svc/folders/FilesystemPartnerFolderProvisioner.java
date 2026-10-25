package edi.partners.svc.folders;

import edi.partners.domain.Partner;
import edi.partners.svc.StorageLayout;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Crea el arbol estandar del socio en disco. Las carpetas que ya existen se conservan (p. ej. al
 * reutilizar el identificador de un socio dado de baja). Si la transaccion del alta se deshace, se
 * quitan las carpetas que creo esta llamada, y solo si siguen vacias: nunca se borra un archivo.
 */
@Component
public class FilesystemPartnerFolderProvisioner implements PartnerFolderProvisioner {

    private static final Logger log = LoggerFactory.getLogger(FilesystemPartnerFolderProvisioner.class);

    private final StorageLayout layout;

    public FilesystemPartnerFolderProvisioner(StorageLayout layout) {
        this.layout = layout;
    }

    @Override
    public void provision(Partner partner) {
        List<Path> created = create(partner.getAs2Name());
        if (!created.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        undo(created);
                    }
                }
            });
        }
    }

    /** Crea lo que falte del arbol; devuelve lo creado, de la raiz del socio hacia dentro. */
    public List<Path> create(String as2Name) {
        Path base = layout.partnerRoot(as2Name);
        List<Path> created = new ArrayList<>();
        try {
            Files.createDirectories(layout.root()); // la raiz es de todos: no se quita al deshacer
            mkdir(base, created);
            for (String sub : StorageLayout.TREE) {
                mkdir(base.resolve(sub), created);
            }
            return created;
        } catch (IOException | RuntimeException e) {
            undo(created);
            throw new PartnerFolderException("No se pudieron crear las carpetas de " + as2Name + " en " + base, e);
        }
    }

    private static void mkdir(Path dir, List<Path> created) throws IOException {
        if (Files.isSymbolicLink(dir) || Files.isDirectory(dir)) {
            return;
        }
        Files.createDirectories(dir);
        created.add(dir);
    }

    /** Quita lo creado en orden inverso; una carpeta con contenido se deja donde esta. */
    static void undo(List<Path> created) {
        for (int i = created.size() - 1; i >= 0; i--) {
            Path dir = created.get(i);
            try {
                Files.deleteIfExists(dir);
            } catch (DirectoryNotEmptyException e) {
                log.warn("Se conserva {}: ya tiene contenido", dir);
            } catch (IOException e) {
                log.warn("No se pudo quitar {}: {}", dir, e.toString());
            }
        }
    }
}
