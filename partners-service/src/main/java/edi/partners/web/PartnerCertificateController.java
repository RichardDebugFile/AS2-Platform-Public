package edi.partners.web;

import edi.partners.domain.KeyUsage;
import edi.partners.dto.CertificateChangeResponse;
import edi.partners.dto.CertificateResponse;
import edi.partners.dto.CertificateUpdateRequest;
import edi.partners.dto.CertificateUploadResponse;
import edi.partners.svc.PartnerCertificateService;
import edi.partners.svc.PartnerCertificateService.PemFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Certificados X.509 de los socios (HU-11). Todos los roles consultan; ADMIN y OPERATOR cargan y
 * cambian su estado, y solo ADMIN los elimina (lo exige el gateway).
 */
@RestController
@RequestMapping("/api/partners/{partnerId}/certificates")
public class PartnerCertificateController {

    private static final MediaType PEM = MediaType.parseMediaType("application/x-pem-file");

    private final PartnerCertificateService certificates;

    public PartnerCertificateController(PartnerCertificateService certificates) {
        this.certificates = certificates;
    }

    @GetMapping
    public List<CertificateResponse> list(@PathVariable UUID partnerId) {
        return certificates.list(partnerId);
    }

    @GetMapping("/{certificateId}")
    public CertificateResponse get(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        return certificates.get(partnerId, certificateId);
    }

    @GetMapping("/{certificateId}/pem")
    public ResponseEntity<String> pem(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        PemFile f = certificates.pem(partnerId, certificateId);
        return ResponseEntity.ok().contentType(PEM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .body(f.content());
    }

    /** Archivo PEM o DER en {@code file} y su uso en {@code keyUsage}. 201 si es nuevo; 200 si ya estaba. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CertificateUploadResponse> upload(@PathVariable UUID partnerId,
                                                            @RequestPart("file") MultipartFile file,
                                                            @RequestParam("keyUsage") KeyUsage keyUsage,
                                                            @RequestParam(value = "description", required = false)
                                                            String description) throws IOException {
        CertificateUploadResponse r = certificates.upload(partnerId, keyUsage, file.getBytes(), description);
        HttpStatus status = r.outcome() == CertificateUploadResponse.Outcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(r);
    }

    @PostMapping("/{certificateId}/prefer")
    public CertificateChangeResponse prefer(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        return certificates.prefer(partnerId, certificateId);
    }

    @PostMapping("/{certificateId}/activate")
    public CertificateChangeResponse activate(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        return certificates.activate(partnerId, certificateId);
    }

    @PostMapping("/{certificateId}/deactivate")
    public CertificateChangeResponse deactivate(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        return certificates.deactivate(partnerId, certificateId);
    }

    @PutMapping("/{certificateId}")
    public CertificateResponse update(@PathVariable UUID partnerId, @PathVariable UUID certificateId,
                                      @Valid @RequestBody CertificateUpdateRequest r) {
        return certificates.update(partnerId, certificateId, r.description());
    }

    @DeleteMapping("/{certificateId}")
    public CertificateChangeResponse delete(@PathVariable UUID partnerId, @PathVariable UUID certificateId) {
        return certificates.delete(partnerId, certificateId);
    }
}
