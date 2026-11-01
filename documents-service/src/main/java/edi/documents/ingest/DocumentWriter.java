package edi.documents.ingest;

import edi.documents.schema.X12ValidationService;
import edi.documents.schema.X12ValidationService.Status;
import edi.documents.schema.X12ValidationService.ValidationResult;
import edi.documents.storage.DocumentFileStorage;
import edi.documents.storage.PartnerFolders;
import edi.documents.storage.SafeNames;
import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentMdnRepository;
import edi.documents.store.DocumentPayload;
import edi.documents.store.DocumentPayloadRepository;
import edi.documents.store.DocumentRepository;
import edi.documents.store.DocumentStatus;
import edi.documents.store.PartnerCopyStatus;
import edi.documents.x12.X12Document;
import edi.documents.x12.X12Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Escrituras del repositorio, cada una en su transaccion. La ingesta guarda el registro, el contenido y el
 * archivo en disco como una sola operacion: si el archivo no se puede escribir, la transaccion se revierte
 * y no queda registro; si la base falla al confirmar, el archivo recien escrito se borra.
 */
@Service
public class DocumentWriter {

    /** Resultado de guardar: el documento y si es nuevo o un reintento. */
    public record Stored(Document document, IngestOutcome outcome) {
    }

    private final DocumentRepository documents;
    private final DocumentPayloadRepository payloads;
    private final DocumentMdnRepository mdns;
    private final X12ValidationService validation;
    private final DocumentFileStorage storage;
    private final PartnerFolders folders;
    private final Clock clock;
    private final long maxBytes;

    public DocumentWriter(DocumentRepository documents, DocumentPayloadRepository payloads, DocumentMdnRepository mdns,
            X12ValidationService validation, DocumentFileStorage storage, PartnerFolders folders, Clock clock,
            @Value("${documents.max-size-bytes:10485760}") long maxBytes) {
        this.documents = documents;
        this.payloads = payloads;
        this.mdns = mdns;
        this.validation = validation;
        this.storage = storage;
        this.folders = folders;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public Stored create(IngestCommand c, String user) {
        String partner = SafeNames.folder(c.partner());
        if (c.direction() == null) {
            throw new IllegalArgumentException("Falta la direccion (INBOUND u OUTBOUND)");
        }
        byte[] content = c.content() == null ? new byte[0] : c.content();
        if (content.length == 0) {
            throw new IllegalArgumentException("El documento esta vacio");
        }
        if (content.length > maxBytes) {
            throw new IllegalArgumentException("El documento supera el tamano maximo de " + maxBytes + " bytes");
        }
        String messageId = messageId(c.messageId());
        Optional<Document> existing = documents.findByMessageId(messageId);
        if (existing.isPresent()) {
            return new Stored(existing.get(), IngestOutcome.DUPLICATE);
        }
        if (!folders.exists(partner)) {
            throw new UnknownPartnerException(partner);
        }

        // ISO-8859-1 conserva cada byte tal cual: los numeros de control y separadores no cambian.
        String text = new String(content, StandardCharsets.ISO_8859_1);
        X12Document x12 = X12Parser.parse(text);
        ValidationResult v = validation.validate(text);
        boolean valid = v.status() == Status.VALIDATED;
        Instant now = clock.instant();

        Document d = new Document(UUID.randomUUID(), partner, c.direction(), messageId, now);
        d.describe(x12.typeCode(), x12.version(), x12.interchangeControl(), x12.groupControl(),
                x12.transactionControl());
        d.transport(c.as2From(), c.as2To(), c.mic(), c.micAlgorithm(), c.signingAlgorithm(), c.encryptionAlgorithm());
        d.content(content.length, sha256(content), limit(c.attributes(), 4000), user);
        d.status(statusFor(c.direction(), valid), detail(v));
        Path path = storage.pathFor(c.direction(), bucket(c.direction(), valid), now, partner, x12);
        d.storedAt(path.toString());

        documents.saveAndFlush(d);
        payloads.save(new DocumentPayload(d.getId(), content));
        storage.write(path, content);
        deleteFileOnRollback(path);
        return new Stored(d, IngestOutcome.CREATED);
    }

    /** Copia el documento a la carpeta del socio y anota el resultado (COPIED o PENDING). */
    @Transactional
    public Document copyToPartner(UUID id) {
        Document d = find(id);
        byte[] content = readContent(d);
        boolean valid = d.getStatus() != DocumentStatus.VALIDATION_FAILED;
        String fileName = Path.of(d.getFilePath()).getFileName().toString();
        Optional<Path> copy = folders.copy(d.getPartnerAs2Name(), PartnerFolders.subfolder(d.getDirection(), valid),
                fileName, content);
        d.partnerCopy(copy.map(Path::toString).orElse(null),
                copy.isPresent() ? PartnerCopyStatus.COPIED : PartnerCopyStatus.PENDING, clock.instant());
        return d;
    }

    /** Borra el registro (y por cascada su contenido y MDN) y, al confirmar, su archivo del repositorio. */
    @Transactional
    public Document delete(UUID id) {
        Document d = find(id);
        documents.delete(d);
        documents.flush();
        String path = d.getFilePath();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.deleteQuietly(path);
            }
        });
        return d;
    }

    @Transactional
    public void deleteMdn(UUID id) {
        if (!mdns.existsById(id)) {
            throw new DocumentNotFoundException("El documento " + id + " no tiene MDN");
        }
        mdns.deleteById(id);
    }

    /** Contenido desde la base; si faltara, desde el archivo del repositorio. */
    @Transactional(readOnly = true)
    public byte[] content(Document d) {
        return readContent(d);
    }

    /** Sin pasar por el proxy transaccional: lo usan los metodos de esta clase dentro de su transaccion. */
    private byte[] readContent(Document d) {
        return payloads.findById(d.getId()).map(DocumentPayload::getContent).orElseGet(() -> readFile(d));
    }

    private Document find(UUID id) {
        return documents.findById(id).orElseThrow(() -> new DocumentNotFoundException("No existe el documento " + id));
    }

    private static byte[] readFile(Document d) {
        try {
            return Files.readAllBytes(Path.of(d.getFilePath()));
        } catch (java.io.IOException _) {
            throw new DocumentNotFoundException("El contenido del documento " + d.getId() + " no esta disponible");
        }
    }

    private void deleteFileOnRollback(Path path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.deleteQuietly(path.toString());
                }
            }
        });
    }

    static DocumentStatus statusFor(Direction direction, boolean valid) {
        if (!valid) {
            return DocumentStatus.VALIDATION_FAILED;
        }
        return direction == Direction.INBOUND ? DocumentStatus.RECEIVED : DocumentStatus.CREATED;
    }

    /** Carpeta del repositorio segun el resultado: inbound/{success|failed}, outbound/{created|failed}. */
    static String bucket(Direction direction, boolean valid) {
        if (!valid) {
            return "failed";
        }
        return direction == Direction.INBOUND ? "success" : "created";
    }

    static String messageId(String raw) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) {
            return "<" + UUID.randomUUID() + "@as2-platform>";
        }
        if (v.length() > 255) {
            throw new IllegalArgumentException("El Message-ID supera 255 caracteres");
        }
        return v;
    }

    private static String detail(ValidationResult v) {
        List<String> parts = new ArrayList<>();
        if (!v.missingSegments().isEmpty()) {
            parts.add("Faltan los segmentos: " + String.join(", ", v.missingSegments()));
        }
        parts.addAll(v.problems());
        return limit(String.join(". ", parts), 2000);
    }

    private static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
