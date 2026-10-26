package edi.partners.web;

import edi.partners.domain.KeyUsage;
import edi.partners.dto.InternalCertificateView;
import edi.partners.svc.PartnerCertificateService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura de certificados por el motor AS2 (HU-15): los activos de un socio y uso, el preferente
 * primero, y uno por su id ({@code db://partner_certificate/<id>}). Red interna: el gateway no lo enruta.
 */
@RestController
@RequestMapping("/internal")
public class InternalCertificateController {

    private final PartnerCertificateService certificates;

    public InternalCertificateController(PartnerCertificateService certificates) {
        this.certificates = certificates;
    }

    @GetMapping("/partners/{partnerId}/certificates")
    public List<InternalCertificateView> active(@PathVariable UUID partnerId, @RequestParam("usage") KeyUsage usage) {
        return certificates.activeForEngine(partnerId, usage);
    }

    @GetMapping("/certificates/{certificateId}")
    public InternalCertificateView one(@PathVariable UUID certificateId) {
        return certificates.forEngine(certificateId);
    }
}
