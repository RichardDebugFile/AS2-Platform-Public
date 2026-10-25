package edi.partners.web;

import edi.partners.dto.InternalPartnerView;
import edi.partners.svc.InternalPartnerService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultas internas del motor AS2. El gateway no enruta {@code /internal/partners/**}: solo son
 * alcanzables dentro de la red de contenedores.
 */
@RestController
@RequestMapping("/internal/partners")
public class InternalPartnerController {

    private final InternalPartnerService internal;

    public InternalPartnerController(InternalPartnerService internal) {
        this.internal = internal;
    }

    @GetMapping("/search")
    public InternalPartnerView search(@RequestParam("as2Name") String as2Name) {
        return internal.byAs2Name(as2Name);
    }

    @GetMapping("/filewatcher-enabled")
    public List<String> fileWatcherEnabled() {
        return internal.fileWatcherEnabled();
    }
}
