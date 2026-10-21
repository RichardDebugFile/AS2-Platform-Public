package edi.partners.svc;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carpetas por defecto de cada socio bajo la raiz de almacenamiento:
 * {@code <raiz>/<as2Name>/Outgoing|Incoming|MDN|Archive}. HU-09 las crea en disco.
 */
@Component
public class StorageLayout {

    public static final String OUTGOING = "Outgoing";
    public static final String INCOMING = "Incoming";
    public static final String MDN = "MDN";
    public static final String ARCHIVE = "Archive";

    private final Path root;

    public StorageLayout(@Value("${partners.storage.root:/opt/as2_data/partners}") String root) {
        this.root = Path.of(root);
    }

    public String folder(String as2Name, String subfolder) {
        return root.resolve(as2Name).resolve(subfolder).toString();
    }
}
