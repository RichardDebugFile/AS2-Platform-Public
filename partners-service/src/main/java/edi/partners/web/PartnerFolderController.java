package edi.partners.web;

import edi.partners.dto.FolderPrecheckResponse;
import edi.partners.dto.FolderStatusResponse;
import edi.partners.dto.PartnerResponse;
import edi.partners.dto.ProductionRequest;
import edi.partners.dto.SharedFolderRequest;
import edi.partners.svc.PartnerFolderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carpetas de los socios (HU-09). El gateway reserva a ADMIN el enlace y la produccion, que cambian
 * lo que ve el ERP.
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerFolderController {

    private final PartnerFolderService folders;

    public PartnerFolderController(PartnerFolderService folders) {
        this.folders = folders;
    }

    @GetMapping("/folder-statuses")
    public List<FolderStatusResponse> statuses() {
        return folders.statuses();
    }

    @GetMapping("/{id}/folders")
    public FolderStatusResponse status(@PathVariable UUID id) {
        return folders.status(id);
    }

    @PostMapping("/{id}/folders/repair")
    public FolderStatusResponse repair(@PathVariable UUID id) {
        return folders.repair(id);
    }

    @PostMapping("/{id}/folders/precheck")
    public FolderPrecheckResponse precheck(@PathVariable UUID id, @Valid @RequestBody SharedFolderRequest r) {
        return folders.precheck(id, r.sharedFolderName());
    }

    @PostMapping("/{id}/folders/link")
    public FolderStatusResponse link(@PathVariable UUID id, @Valid @RequestBody SharedFolderRequest r) {
        return folders.link(id, r.sharedFolderName());
    }

    @PostMapping("/{id}/production")
    public PartnerResponse production(@PathVariable UUID id, @Valid @RequestBody ProductionRequest r) {
        return folders.production(id, r.enabled(), r.confirm());
    }
}
