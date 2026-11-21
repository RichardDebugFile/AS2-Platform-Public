package edi.documents.web;

import edi.documents.ingest.DocumentWriter;
import edi.documents.store.DocumentStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Resultado de un envío registrado por el adaptador en la red interna. */
@RestController
public class InternalAs2Controller {

    private final DocumentWriter writer;

    public InternalAs2Controller(DocumentWriter writer) {
        this.writer = writer;
    }

    public record OutboundResult(DocumentStatus status, String detail, DocumentWriter.Receipt receipt) {}

    @PutMapping("/internal/as2/documents/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID id, @RequestBody OutboundResult result) {
        writer.completeOutbound(id, result.status(), result.detail(), result.receipt());
    }
}
