package edi.partners.dto;

import java.util.List;

/**
 * Resultado de la verificacion previa al enlace con la carpeta compartida.
 *
 * @param ok       si se puede enlazar (sin problemas)
 * @param target   carpeta del socio en el recurso compartido
 * @param problems lo que impide enlazar
 * @param actions  lo que hara el enlace (archivos que se moveran, enlaces que se crearan)
 */
public record FolderPrecheckResponse(boolean ok, String sharedFolderName, String target, List<String> problems,
                                     List<String> actions) {
}
