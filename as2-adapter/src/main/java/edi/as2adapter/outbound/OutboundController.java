package edi.as2adapter.outbound;

import edi.as2adapter.as2.As2Exception;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboundController {

    private final OutboundService outbound;

    public OutboundController(OutboundService outbound) {
        this.outbound = outbound;
    }

    @PostMapping("/api/as2/send")
    public OutboundService.Outcome send(@RequestParam String partner,
                                        @RequestParam(defaultValue = "documento.edi") String fileName,
                                        @RequestBody byte[] document,
                                        @RequestHeader(value = "X-User-Name", required = false) String user) {
        return outbound.send(partner, document, fileName, user);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(As2Exception.class)
    ResponseEntity<Map<String, String>> dependencyFailure(As2Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
