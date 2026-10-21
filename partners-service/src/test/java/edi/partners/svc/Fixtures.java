package edi.partners.svc;

import edi.partners.domain.MdnMode;
import edi.partners.domain.TransportType;
import edi.partners.dto.PartnerRequest;

/** Peticiones de prueba con datos ficticios. */
final class Fixtures {

    private Fixtures() {
    }

    static PartnerRequest request(String as2Name) {
        return request(as2Name, null, null, null, null);
    }

    static PartnerRequest request(String as2Name, MdnMode mdnMode, String mdnUrl, String user, String password) {
        return new PartnerRequest(as2Name, "Socio de prueba", "NOSOTROS-AS2", null, null, null,
                "as2.socio.example", 443, TransportType.HTTPS, "as2", user, password,
                null, null, null, null,
                null, null, null, null, null, null, mdnMode, mdnUrl, null, null, null,
                null, null, null, null,
                null, null, "ops@example.com", null, null);
    }
}
