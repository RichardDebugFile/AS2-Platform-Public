package edi.as2adapter.partner;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartnerTlsController {

    private final PartnerTls tls;

    public PartnerTlsController(PartnerTls tls) {
        this.tls = tls;
    }

    @PostMapping("/internal/as2/tls-cache/invalidate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invalidate(@RequestParam UUID partnerId) {
        tls.invalidate(partnerId);
    }
}
