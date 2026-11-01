package edi.documents.storage;

import edi.documents.store.Direction;
import edi.documents.x12.X12Document;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Repositorio de documentos en disco: una copia legible de cada documento, organizada para encontrarla a
 * mano si hiciera falta:
 * {@code <raiz>/{inbound|outbound}/{resultado}/AAAAMMDD/{tipo}/{socio}/EDI_{tipo}_{version}_{ISA}_{GS}_{ST}_{marca}.edi}.
 *
 * <p>Cada archivo se escribe en un temporal de la misma carpeta y se renombra al final: nunca queda un
 * archivo a medias con el nombre definitivo.</p>
 */
@Component
public class DocumentFileStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final Path root;
    private final ZoneId zone;

    public DocumentFileStorage(@Value("${documents.storage.root:/opt/as2_data}") String root,
            @Value("${documents.zone:America/Guayaquil}") String zone) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.zone = ZoneId.of(zone);
    }

    public Path root() {
        return root;
    }

    /**
     * Ruta del documento en el repositorio: {@code <raiz>/<direccion>/<resultado>/AAAAMMDD/<tipo>/<socio>/}
     * y un nombre con el tipo, la version y los numeros de control. Cada parte se sanea con {@link SafeNames}.
     */
    public Path pathFor(Direction direction, String bucket, Instant at, String partner, X12Document x12) {
        String type = SafeNames.part(x12.typeCode());
        String name = String.join("_", "EDI", type, SafeNames.part(x12.version()),
                SafeNames.part(x12.interchangeControl()), SafeNames.part(x12.groupControl()),
                SafeNames.part(x12.transactionControl()), STAMP.format(at.atZone(zone))) + ".edi";
        Path p = root.resolve(direction.folder()).resolve(bucket).resolve(DAY.format(at.atZone(zone))).resolve(type)
                .resolve(SafeNames.folder(partner)).resolve(name).normalize();
        if (!p.startsWith(root)) {
            throw new StorageException("Ruta fuera del repositorio: " + p, null);
        }
        return p;
    }

    /** Escribe el archivo de forma atomica; si algo falla lanza {@link StorageException} y no deja el archivo. */
    public void write(Path target, byte[] content) {
        try {
            writeAtomically(target, content);
        } catch (IOException | RuntimeException e) {
            throw new StorageException("No se pudo guardar el documento en el repositorio (" + target.getParent() + ")",
                    e);
        }
    }

    /** Escribe en un temporal de la carpeta destino y lo renombra; la carpeta debe poder crearse. */
    static void writeAtomically(Path target, byte[] content) throws IOException {
        Path dir = target.getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, ".", ".tmp");
        try {
            Files.write(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public boolean exists(String path) {
        return path != null && Files.isRegularFile(Path.of(path));
    }

    public void deleteQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException _) {
            // Lo reporta el control de consistencia como archivo huerfano.
        }
    }

    /** Todos los archivos .edi del repositorio (entrada y salida), para el control de consistencia. */
    public List<Path> allFiles() {
        List<Path> files = new ArrayList<>();
        for (Direction d : Direction.values()) {
            Path base = root.resolve(d.folder());
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(base)) {
                s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".edi"))
                        .forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }
}
