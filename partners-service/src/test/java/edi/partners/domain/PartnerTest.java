package edi.partners.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PartnerTest {

    private Partner partner(TransportType transport, int port, String path) {
        Partner p = new Partner();
        p.setAs2Name("SOCIO-A");
        p.setServerAddress("as2.socio-a.example");
        p.setTransportType(transport);
        p.setPort(port);
        p.setPath(path);
        return p;
    }

    @Test
    void nuevoSocio_tieneIdYValoresPorDefectoSeguros() {
        Partner p = new Partner();

        assertThat(p.getId()).isNotNull();
        assertThat(p.isActive()).isTrue();
        assertThat(p.isRequestMdn()).isTrue();
        assertThat(p.isRequestSignedMdn()).isTrue();
        assertThat(p.getMdnMode()).isEqualTo(MdnMode.SYNC);
        assertThat(p.isValidateCertificate()).isTrue();
        assertThat(p.isValidateSignature()).isTrue();
        assertThat(p.isValidateMic()).isTrue();
        assertThat(p.isEnableDualStorage()).isTrue();
        assertThat(p.isEnableIncomingQueue()).isFalse();
        assertThat(p.getConnectionTimeoutSeconds()).as("sin valor = el global del motor AS2").isNull();
    }

    @Test
    void urlDelServidor_unaProtocoloHostPuertoYRuta() {
        assertThat(partner(TransportType.HTTPS, 4443, "/as2/receive").endpointUrl())
                .isEqualTo("https://as2.socio-a.example:4443/as2/receive");
        assertThat(partner(TransportType.HTTP, 8080, "as2").endpointUrl())
                .isEqualTo("http://as2.socio-a.example:8080/as2");
        assertThat(partner(TransportType.HTTPS, 443, null).endpointUrl())
                .isEqualTo("https://as2.socio-a.example:443");
    }

    @Test
    void idsDistintosPorSocio() {
        assertThat(new Partner().getId()).isNotEqualTo(new Partner().getId());
    }
}
