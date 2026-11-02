package edi.partners.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Las pruebas que enlazan carpetas necesitan crear enlaces simbolicos. En Linux (CI y contenedores)
 * siempre se puede; en Windows solo con el Modo de desarrollador o como administrador. Sin ese
 * permiso esas pruebas se omiten en lugar de fallar: el servicio se ejecuta en contenedores Linux.
 */
public final class Symlinks {

    public static final boolean SUPPORTED = probe();

    public static final String WHY = "El sistema no permite crear enlaces simbolicos (Windows sin Modo de desarrollador)";

    private Symlinks() {
    }

    private static boolean probe() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("symlink-probe");
            Files.createSymbolicLink(dir.resolve("enlace"), dir.resolve("destino"));
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException _) {
            return false;
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve("enlace"));
            Files.deleteIfExists(dir);
        } catch (IOException _) {
            // carpeta temporal: el sistema la limpia
        }
    }
}
