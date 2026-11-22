package edi.as2adapter.inbound;

import edi.as2adapter.as2.As2Exception;
import edi.as2adapter.as2.MessageBuilder;
import edi.as2adapter.as2.Mime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboundController {

    private final InboundService inbound;
    private final InboundFollowUp followUp;

    public InboundController(InboundService inbound, InboundFollowUp followUp) {
        this.inbound = inbound;
        this.followUp = followUp;
    }

    @GetMapping("/as2/receive")
    public ResponseEntity<Void> available() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/as2/receive")
    public void receive(HttpServletRequest request, HttpServletResponse response, @RequestBody byte[] body)
            throws IOException {
        handle(request, response, body, false);
    }

    void handle(HttpServletRequest http, HttpServletResponse response, byte[] body, boolean testMode) throws IOException {
        var request = new InboundService.Request(requiredHeader(http, "AS2-From"), requiredHeader(http, "AS2-To"),
                requiredHeader(http, "Message-ID"), http.getHeader("Content-Type"),
                http.getHeader("Content-Transfer-Encoding"), http.getHeader("Disposition-Notification-To"),
                http.getHeader("Disposition-Notification-Options"), http.getHeader("Receipt-Delivery-Option"));
        var result = inbound.receive(request, body, testMode);
        response.setStatus(HttpServletResponse.SC_OK);
        if (result.body() != null) {
            response.setHeader("AS2-Version", "1.2");
            response.setHeader("AS2-From", MessageBuilder.quote(Mime.unquote(request.to().strip())));
            response.setHeader("AS2-To", request.from().strip());
            response.setHeader("MIME-Version", "1.0");
            response.setHeader("Content-Type", result.body().contentType());
            response.setContentLength(result.body().content().length);
            response.getOutputStream().write(result.body().content());
        } else {
            response.setContentLength(0);
        }
        response.flushBuffer();
        followUp.queue(result);
    }

    private static String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta la cabecera " + name);
        return value;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(As2Exception.class)
    ResponseEntity<Map<String, String>> unavailable(As2Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
