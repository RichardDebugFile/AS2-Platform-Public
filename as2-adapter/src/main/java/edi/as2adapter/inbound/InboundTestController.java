package edi.as2adapter.inbound;

import edi.as2adapter.as2.As2Exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local & !prod", "test & !prod"})
@RestController
class InboundTestController {

    private final InboundController inbound;

    InboundTestController(InboundController inbound) {
        this.inbound = inbound;
    }

    @PostMapping("/as2/receive/test")
    public void receive(HttpServletRequest request, HttpServletResponse response, @RequestBody byte[] body)
            throws IOException {
        inbound.handle(request, response, body, true);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return inbound.badRequest(e);
    }

    @ExceptionHandler(As2Exception.class)
    ResponseEntity<Map<String, String>> unavailable(As2Exception e) {
        return inbound.unavailable(e);
    }
}
