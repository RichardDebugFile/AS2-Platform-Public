package edi.partners.web;

import edi.partners.dto.AlgorithmSuggestion;
import edi.partners.dto.CompletePartnerRequest;
import edi.partners.dto.CompletePartnerResponse;
import edi.partners.dto.PartnerRequest;
import edi.partners.dto.PartnerResponse;
import edi.partners.dto.PartnerStatusRequest;
import edi.partners.svc.AlgorithmPolicy;
import edi.partners.svc.CompletePartnerService;
import edi.partners.svc.PartnerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Socios comerciales. Los permisos por rol los aplica el gateway: AUDITOR solo consulta y el
 * borrado definitivo es solo para ADMIN.
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService partners;
    private final CompletePartnerService complete;
    private final AlgorithmPolicy algorithms;

    public PartnerController(PartnerService partners, CompletePartnerService complete, AlgorithmPolicy algorithms) {
        this.partners = partners;
        this.complete = complete;
        this.algorithms = algorithms;
    }

    @GetMapping
    public List<PartnerResponse> active() {
        return partners.list(true);
    }

    @GetMapping("/inactive")
    public List<PartnerResponse> inactive() {
        return partners.list(false);
    }

    @GetMapping("/{id}")
    public PartnerResponse get(@PathVariable UUID id) {
        return partners.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerResponse create(@RequestBody @Valid PartnerRequest req) {
        return PartnerResponse.of(partners.create(req));
    }

    /** Alta en un paso: socio, carpetas y configuraciones; si algo falla no queda nada. */
    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletePartnerResponse createComplete(@RequestBody @Valid CompletePartnerRequest req) {
        return complete.create(req);
    }

    @PutMapping("/{id}")
    public PartnerResponse update(@PathVariable UUID id, @RequestBody @Valid PartnerRequest req) {
        return partners.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public PartnerResponse setStatus(@PathVariable UUID id, @RequestBody @Valid PartnerStatusRequest req) {
        return partners.setActive(id, req.active());
    }

    /** Baja logica. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        partners.delete(id);
    }

    /** Borrado definitivo con sus configuraciones; {@code confirm} = identificador AS2. */
    @DeleteMapping("/{id}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@PathVariable UUID id, @RequestParam("confirm") String confirm) {
        partners.purge(id, confirm);
    }

    @GetMapping("/{id}/algorithm-suggestion")
    public AlgorithmSuggestion algorithmSuggestion(@PathVariable UUID id,
            @RequestParam(name = "legacyCompatibility", defaultValue = "false") boolean legacyCompatibility) {
        partners.get(id);
        return algorithms.suggest(legacyCompatibility);
    }
}
