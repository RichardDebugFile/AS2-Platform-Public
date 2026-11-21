package edi.partners.web;

import edi.partners.dto.DocumentTypeRequest;
import edi.partners.dto.DocumentTypeResponse;
import edi.partners.dto.PartnerDocumentTypeRequest;
import edi.partners.dto.PartnerDocumentTypeResponse;
import edi.partners.svc.DocumentTypeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de tipos de documento EDI y tipos de cada socio (HU-13). El gateway deja consultar a todos
 * los roles y modificar a ADMIN y OPERATOR.
 */
@RestController
public class DocumentTypeController {

    private static final String CATALOG = "/api/catalogs/document-types";

    private final DocumentTypeService service;

    public DocumentTypeController(DocumentTypeService service) {
        this.service = service;
    }

    @GetMapping(CATALOG)
    public List<DocumentTypeResponse> list() {
        return service.list();
    }

    @GetMapping(CATALOG + "/{id}")
    public DocumentTypeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping(CATALOG)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(@Valid @RequestBody DocumentTypeRequest request) {
        return service.create(request);
    }

    @PutMapping(CATALOG + "/{id}")
    public DocumentTypeResponse update(@PathVariable UUID id, @Valid @RequestBody DocumentTypeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping(CATALOG + "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/api/partners/{id}/document-types")
    public List<PartnerDocumentTypeResponse> partnerTypes(@PathVariable UUID id) {
        return service.partnerTypes(id);
    }

    @PutMapping("/api/partners/{id}/document-types")
    public List<PartnerDocumentTypeResponse> replacePartnerTypes(@PathVariable UUID id,
            @Valid @RequestBody PartnerDocumentTypeRequest request) {
        return service.replacePartnerTypes(id, request);
    }
}
