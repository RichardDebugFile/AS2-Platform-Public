package edi.partners.dto;

import edi.partners.domain.FolderState;
import java.util.List;
import java.util.UUID;

/**
 * Estado de las carpetas de un socio.
 *
 * @param health   {@code OK}, {@code MISSING} (falta alguna), {@code NO_ACCESS} (sin permisos) o
 *                 {@code ERROR} (no se pudo comprobar, p. ej. recurso compartido desmontado)
 * @param problems explicacion de cada problema, para mostrar tal cual
 */
public record FolderStatusResponse(UUID partnerId, String as2Name, FolderState folderState, String sharedFolderName,
                                   Health health, List<Folder> folders, List<String> problems) {

    public enum Health { OK, MISSING, NO_ACCESS, ERROR }

    /**
     * @param linked {@code true} si es un enlace simbolico; {@code target} es a donde apunta
     */
    public record Folder(String name, String path, boolean exists, boolean readable, boolean writable,
                         boolean linked, String target) {
    }
}
