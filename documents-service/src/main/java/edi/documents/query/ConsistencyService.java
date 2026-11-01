package edi.documents.query;

import edi.documents.storage.DocumentFileStorage;
import edi.documents.store.DocumentRepository;
import edi.documents.store.DocumentRepository.FileRef;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compara la base con el repositorio en disco: registros cuyo archivo falta (alguien lo borro o se perdio)
 * y archivos que ningun registro reclama (huerfanos, p. ej. de una ingesta que fallo al confirmar).
 */
@Service
public class ConsistencyService {

    static final int MAX_LISTED = 500;

    public record Report(Instant checkedAt, boolean consistent, int documents, int files,
            List<FileRef> missingFiles, List<String> orphanFiles) {
    }

    private final DocumentRepository documents;
    private final DocumentFileStorage storage;
    private final Clock clock;

    public ConsistencyService(DocumentRepository documents, DocumentFileStorage storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Report documents() {
        List<FileRef> refs = documents.fileRefs();
        Set<Path> known = new HashSet<>();
        for (FileRef r : refs) {
            known.add(Path.of(r.filePath()).toAbsolutePath().normalize());
        }
        List<FileRef> missing = refs.stream().filter(r -> !storage.exists(r.filePath())).limit(MAX_LISTED).toList();
        List<Path> files = storage.allFiles();
        List<String> orphans = files.stream().map(p -> p.toAbsolutePath().normalize())
                .filter(p -> !known.contains(p)).map(p -> storage.root().relativize(p).toString()).limit(MAX_LISTED)
                .toList();
        return new Report(clock.instant(), missing.isEmpty() && orphans.isEmpty(), refs.size(), files.size(), missing,
                orphans);
    }
}
