package edi.documents.storage;

import edi.documents.store.Direction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carpetas de los socios que crea partners-service (HU-09):
 * {@code <raiz>/<as2Name>/{Incoming, Archive, ...}}. {@code Incoming} puede ser un enlace a la carpeta
 * compartida del ERP: si el servidor de archivos esta caido, la copia falla y queda pendiente.
 *
 * <p>Aqui no se crea ninguna carpeta: si falta, es que el socio no esta registrado o que la carpeta
 * compartida no esta disponible, y crearla ocultaria el problema.</p>
 */
@Component
public class PartnerFolders {

    public static final String INCOMING = "Incoming";
    public static final String ARCHIVE = "Archive";
    public static final String ERROR = "Error";
    private static final Logger log = LoggerFactory.getLogger(PartnerFolders.class);

    private final Path root;

    public PartnerFolders(@Value("${documents.partners.root:/opt/as2_data/partners}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** El socio tiene su carpeta (lo registro partners-service). */
    public boolean exists(String as2Name) {
        return Files.isDirectory(root.resolve(SafeNames.folder(as2Name)));
    }

    /**
     * Subcarpeta de destino: un documento de entrada valido va a {@code Incoming} (la lee el ERP), uno de
     * salida a {@code Archive}, y uno que no paso la validacion a {@code Error} para revisarlo a mano.
     */
    public static String subfolder(Direction direction, boolean valid) {
        if (!valid) {
            return ERROR;
        }
        return direction == Direction.INBOUND ? INCOMING : ARCHIVE;
    }

    public Path target(String as2Name, String subfolder, String fileName) {
        return root.resolve(SafeNames.folder(as2Name)).resolve(subfolder).resolve(fileName);
    }

    /** Copia el documento; si no se puede, lo registra y devuelve vacio (la copia queda pendiente). */
    public Optional<Path> copy(String as2Name, String subfolder, String fileName, byte[] content) {
        Path target = target(as2Name, subfolder, fileName);
        Path dir = target.getParent();
        if (!Files.isDirectory(dir)) {
            log.warn("Carpeta del socio no disponible, copia pendiente: {}", dir);
            return Optional.empty();
        }
        try {
            DocumentFileStorage.writeAtomically(target, content);
            return Optional.of(target);
        } catch (IOException e) {
            log.warn("No se pudo copiar a la carpeta del socio, copia pendiente: {} ({})", target, e.toString());
            return Optional.empty();
        }
    }

    public boolean present(String path) {
        return path != null && Files.isRegularFile(Path.of(path));
    }
}
