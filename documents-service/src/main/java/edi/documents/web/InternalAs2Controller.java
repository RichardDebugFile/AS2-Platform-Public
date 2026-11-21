package edi.documents.web;

import edi.documents.ingest.DocumentWriter;
import edi.documents.store.DocumentStatus;
import edi.documents.x12.X12Generator;
import edi.documents.x12.X12Parser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Resultado de un envío registrado por el adaptador en la red interna. */
@RestController
public class InternalAs2Controller {

    private final DocumentWriter writer;
    private final Clock clock;

    public InternalAs2Controller(DocumentWriter writer, Clock clock) {
        this.writer = writer;
        this.clock = clock;
    }

    public record OutboundResult(DocumentStatus status, String detail, DocumentWriter.Receipt receipt) {}

    @PutMapping("/internal/as2/documents/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID id, @RequestBody OutboundResult result) {
        writer.completeOutbound(id, result.status(), result.detail(), result.receipt());
    }

    @PostMapping(value = "/internal/as2/ack997", consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE + ";charset=ISO-8859-1")
    public String acknowledgment(@RequestBody String original) {
        var document = X12Parser.parse(original);
        long control = ThreadLocalRandom.current().nextLong(1, 1_000_000_000L);
        return X12Generator.functionalAck(document, "P".equals(document.usageIndicator()), control,
                LocalDateTime.now(clock));
    }
}
