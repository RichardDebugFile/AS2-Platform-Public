package edi.partners.web;

import edi.partners.dto.IncomingConfigRequest;
import edi.partners.dto.IncomingConfigResponse;
import edi.partners.dto.MdnConfigRequest;
import edi.partners.dto.MdnConfigResponse;
import edi.partners.dto.OutgoingConfigRequest;
import edi.partners.dto.OutgoingConfigResponse;
import edi.partners.svc.PartnerConfigService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuraciones de salida, entrada y MDN de un socio. PUT crea o reemplaza. */
@RestController
@RequestMapping("/api/partners/{id}")
public class PartnerConfigController {

    private final PartnerConfigService configs;

    public PartnerConfigController(PartnerConfigService configs) {
        this.configs = configs;
    }

    @GetMapping("/outgoing-config")
    public OutgoingConfigResponse getOutgoing(@PathVariable UUID id) {
        return configs.getOutgoing(id);
    }

    @PutMapping("/outgoing-config")
    public OutgoingConfigResponse putOutgoing(@PathVariable UUID id, @RequestBody @Valid OutgoingConfigRequest req) {
        return configs.upsertOutgoing(id, req);
    }

    @GetMapping("/incoming-config")
    public IncomingConfigResponse getIncoming(@PathVariable UUID id) {
        return configs.getIncoming(id);
    }

    @PutMapping("/incoming-config")
    public IncomingConfigResponse putIncoming(@PathVariable UUID id, @RequestBody @Valid IncomingConfigRequest req) {
        return configs.upsertIncoming(id, req);
    }

    @GetMapping("/mdn-config")
    public MdnConfigResponse getMdn(@PathVariable UUID id) {
        return configs.getMdn(id);
    }

    @PutMapping("/mdn-config")
    public MdnConfigResponse putMdn(@PathVariable UUID id, @RequestBody @Valid MdnConfigRequest req) {
        return configs.upsertMdn(id, req);
    }
}
