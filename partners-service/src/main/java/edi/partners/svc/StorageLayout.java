package edi.partners.svc;

import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carpetas de cada socio (HU-09): {@code <raiz>/<as2Name>/{Archive,Certs,Error,Incoming,MDN,Outgoing}}
 * y, en produccion, {@code Incoming_queue}. El recurso compartido del ERP se monta en otra raiz
 * ({@code <montaje>/<carpeta compartida>/<subcarpeta>}).
 */
@Component
public class StorageLayout {

    public static final String ARCHIVE = "Archive";
    public static final String CERTS = "Certs";
    public static final String ERROR = "Error";
    public static final String INCOMING = "Incoming";
    public static final String MDN = "MDN";
    public static final String OUTGOING = "Outgoing";
    /** Cola de la que lee el ERP; solo existe con el socio en produccion. */
    public static final String INCOMING_QUEUE = "Incoming_queue";

    /** Arbol estandar que se crea al registrar el socio. */
    public static final List<String> TREE = List.of(ARCHIVE, CERTS, ERROR, INCOMING, MDN, OUTGOING);

    private final Path root;
    private final Path sharedRoot;

    @Autowired
    public StorageLayout(@Value("${partners.storage.root:/opt/as2_data/partners}") String root,
                         @Value("${partners.shared.root:/mnt/erp}") String sharedRoot) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.sharedRoot = Path.of(sharedRoot).toAbsolutePath().normalize();
    }

    public StorageLayout(String root) {
        this(root, "/mnt/erp");
    }

    public String folder(String as2Name, String subfolder) {
        return partnerRoot(as2Name).resolve(subfolder).toString();
    }

    /** Carpeta del socio; el nombre ya viene validado como nombre de carpeta (sin rutas). */
    public Path partnerRoot(String as2Name) {
        return inside(root, as2Name);
    }

    public Path root() {
        return root;
    }

    public Path sharedRoot() {
        return sharedRoot;
    }

    /** Carpeta del socio en el recurso compartido. */
    public Path shared(String sharedFolderName) {
        return inside(sharedRoot, sharedFolderName);
    }

    /** Defensa adicional contra rutas fuera de la raiz aunque el nombre llegara sin validar. */
    private static Path inside(Path base, String name) {
        Path p = base.resolve(name).normalize();
        if (!p.getParent().equals(base)) {
            throw new IllegalArgumentException("Nombre de carpeta no valido: " + name);
        }
        return p;
    }
}
