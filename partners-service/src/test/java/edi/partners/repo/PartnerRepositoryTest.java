package edi.partners.repo;

import static org.assertj.core.api.Assertions.assertThat;

import edi.partners.domain.IncomingConfig;
import edi.partners.domain.MdnConfig;
import edi.partners.domain.MdnMode;
import edi.partners.domain.MdnReceiptType;
import edi.partners.domain.OutgoingConfig;
import edi.partners.domain.Partner;
import edi.partners.domain.SendingMode;
import edi.partners.domain.TransportType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Consultas de los repositorios y auditoria JPA sobre H2 (esquema generado desde las entidades).
 * El esquema real de Flyway y el indice unico parcial se prueban contra PostgreSQL en
 * {@link PartnerSchemaPostgresTest}.
 */
@DataJpaTest
@Import(edi.partners.config.AuditingConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:partners;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class PartnerRepositoryTest {

    @Autowired
    private PartnerRepository partners;

    @Autowired
    private OutgoingConfigRepository outgoing;

    @Autowired
    private IncomingConfigRepository incoming;

    @Autowired
    private MdnConfigRepository mdn;

    @Autowired
    private TestEntityManager em;

    static Partner partner(String as2Name, boolean active) {
        Partner p = new Partner();
        p.setAs2Name(as2Name);
        p.setServerAddress("as2." + as2Name.toLowerCase() + ".example");
        p.setPort(443);
        p.setTransportType(TransportType.HTTPS);
        p.setActive(active);
        return p;
    }

    @Test
    void busquedaPorIdentificadorAs2_ignoraMayusculasYSoloVeActivos() {
        partners.save(partner("SOCIO-A", true));
        partners.save(partner("SOCIO-B", false));

        assertThat(partners.findByAs2NameIgnoreCaseAndActiveTrue("socio-a")).isPresent();
        assertThat(partners.existsByAs2NameIgnoreCaseAndActiveTrue("Socio-A")).isTrue();
        assertThat(partners.findByAs2NameIgnoreCaseAndActiveTrue("SOCIO-B")).isEmpty();
        assertThat(partners.existsByAs2NameIgnoreCaseAndActiveTrue("SOCIO-B")).isFalse();
    }

    @Test
    void duplicadoAlReactivar_excluyeAlPropioSocio() {
        Partner a = partners.save(partner("SOCIO-C", true));

        assertThat(partners.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot("SOCIO-C", a.getId())).isFalse();
        assertThat(partners.existsByAs2NameIgnoreCaseAndActiveTrueAndIdNot("socio-c", java.util.UUID.randomUUID()))
                .isTrue();
    }

    @Test
    void listados_separanActivosEInactivosYOrdenanPorIdentificador() {
        partners.save(partner("ZETA", true));
        partners.save(partner("ALFA", true));
        partners.save(partner("BAJA", false));

        assertThat(partners.findAllByActiveOrderByAs2NameAsc(true))
                .extracting(Partner::getAs2Name).containsExactly("ALFA", "ZETA");
        assertThat(partners.findAllByActiveOrderByAs2NameAsc(false))
                .extracting(Partner::getAs2Name).containsExactly("BAJA");
    }

    @Test
    void auditoria_registraFechasYUsuarioSystemFueraDeUnaPeticion() {
        Partner p = partners.saveAndFlush(partner("SOCIO-D", true));

        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
        assertThat(p.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void configuraciones_seGuardanYSeEncuentranPorSocio() {
        Partner p = partners.save(partner("SOCIO-E", true));

        OutgoingConfig out = new OutgoingConfig(p);
        out.setLocalFolder("/data/SOCIO-E/Outgoing");
        out.setSendingMode(SendingMode.REGULAR);
        outgoing.save(out);
        IncomingConfig in = new IncomingConfig(p);
        in.setIncomingFolder("/data/SOCIO-E/Incoming");
        incoming.save(in);
        MdnConfig m = new MdnConfig(p);
        m.setLocalFolder("/data/SOCIO-E/MDN");
        m.setReceiptType(MdnReceiptType.SIGNED);
        m.setDeliveryMode(MdnMode.SYNC);
        mdn.save(m);
        em.flush();
        em.clear();

        assertThat(outgoing.findByPartnerId(p.getId())).get()
                .extracting(OutgoingConfig::getLocalFolder).isEqualTo("/data/SOCIO-E/Outgoing");
        assertThat(outgoing.existsByPartnerIdAndSendingModeNot(p.getId(), SendingMode.NONE)).isTrue();
        assertThat(incoming.findByPartnerId(p.getId())).isPresent();
        assertThat(mdn.findByPartnerId(p.getId())).get()
                .extracting(MdnConfig::getReceiptType).isEqualTo(MdnReceiptType.SIGNED);
    }
}
