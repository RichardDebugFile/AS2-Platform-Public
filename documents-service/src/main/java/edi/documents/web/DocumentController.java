package edi.documents.web;

import edi.documents.ingest.DocumentIngestService;
import edi.documents.ingest.DocumentIngestService.IngestResult;
import edi.documents.ingest.DocumentWriter;
import edi.documents.ingest.IngestCommand;
import edi.documents.ingest.IngestOutcome;
import edi.documents.query.DocumentQueryService;
import edi.documents.query.DocumentQueryService.Filter;
import edi.documents.query.DocumentQueryService.MdnView;
import edi.documents.query.DocumentQueryService.PageView;
import edi.documents.query.DocumentQueryService.Stats;
import edi.documents.query.DocumentQueryService.StorageStatus;
import edi.documents.query.DocumentView;
import edi.documents.store.Direction;
import edi.documents.store.Document;
import edi.documents.store.DocumentStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Repositorio de documentos (HU-14): ingesta, busqueda, detalle, contenido y borrado. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestService ingest;
    private final DocumentQueryService query;
    private final DocumentWriter writer;

    public DocumentController(DocumentIngestService ingest, DocumentQueryService query, DocumentWriter writer) {
        this.ingest = ingest;
        this.query = query;
        this.writer = writer;
    }

    public record IngestResponse(IngestOutcome outcome, DocumentView document) {
    }

    /**
     * Ingesta: el cuerpo es el documento EDI tal cual; los datos del transporte AS2 van en cabeceras.
     * 201 si es nuevo; 200 con {@code DUPLICATE} si el Message-ID ya estaba (reintento del socio).
     */
    @PostMapping("/ingest/as2")
    public ResponseEntity<IngestResponse> ingest(@RequestParam String partner, @RequestParam Direction direction,
            @RequestBody byte[] content,
            @RequestHeader(value = "Message-ID", required = false) String messageId,
            @RequestHeader(value = "AS2-From", required = false) String as2From,
            @RequestHeader(value = "AS2-To", required = false) String as2To,
            @RequestHeader(value = "X-MIC", required = false) String mic,
            @RequestHeader(value = "X-MIC-Algorithm", required = false) String micAlgorithm,
            @RequestHeader(value = "X-Signing-Algorithm", required = false) String signingAlgorithm,
            @RequestHeader(value = "X-Encryption-Algorithm", required = false) String encryptionAlgorithm,
            @RequestHeader(value = "X-Document-Attributes", required = false) String attributes) {
        IngestResult r = ingest.ingest(new IngestCommand(partner, direction, content, messageId, as2From, as2To, mic,
                micAlgorithm, signingAlgorithm, encryptionAlgorithm, attributes));
        HttpStatus status = r.outcome() == IngestOutcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new IngestResponse(r.outcome(), DocumentView.of(r.document())));
    }

    @GetMapping("/search")
    public PageView<DocumentView> search(@RequestParam(required = false) String partner,
            @RequestParam(required = false) Direction direction, @RequestParam(required = false) String type,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String messageId, @RequestParam(required = false) String isa,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return query.search(new Filter(partner, direction, type, status, from, to, messageId, isa), page, size);
    }

    @GetMapping("/recent")
    public List<DocumentView> recent(@RequestParam(defaultValue = "10") int limit) {
        return query.recent(limit);
    }

    @GetMapping("/stats")
    public Stats stats() {
        return query.stats();
    }

    @GetMapping("/by-message-id")
    public DocumentView byMessageId(@RequestParam String messageId) {
        return query.byMessageId(messageId);
    }

    @GetMapping("/{id}")
    public DocumentView get(@PathVariable UUID id) {
        return DocumentView.of(query.get(id));
    }

    /** Contenido como texto, para el visor de la consola. */
    @GetMapping(value = "/{id}/payload", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> payload(@PathVariable UUID id) {
        byte[] content = writer.content(query.get(id));
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.ISO_8859_1))
                .body(new String(content, StandardCharsets.ISO_8859_1));
    }

    /** Descarga del archivo tal como llego. */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id) {
        Document d = query.get(id);
        String name = DocumentView.of(d).fileName();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(writer.content(d));
    }

    @GetMapping("/{id}/mdn")
    public MdnView mdn(@PathVariable UUID id) {
        return query.mdn(id);
    }

    @GetMapping("/{id}/storage-status")
    public StorageStatus storageStatus(@PathVariable UUID id) {
        return query.storageStatus(id);
    }

    /** Solo ADMIN (lo exige el gateway): borra el registro, su contenido, su MDN y su archivo. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        ingest.delete(id);
    }

    @DeleteMapping("/{id}/mdn")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMdn(@PathVariable UUID id) {
        writer.deleteMdn(id);
    }
}
