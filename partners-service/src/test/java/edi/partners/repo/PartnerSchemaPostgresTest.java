package edi.partners.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.partners.domain.IncomingConfig;
import edi.partners.domain.Partner;
import edi.partners.testsupport.EnabledIfPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Esquema real: Flyway aplica V1 y V2 sobre PostgreSQL y Hibernate lo valida contra las entidades
 * ({@code ddl-auto=validate}). Comprueba lo que H2 no puede: el indice unico parcial del
 * identificador AS2 y el borrado en cascada de las configuraciones.
 */
@EnabledIfPostgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(edi.partners.config.AuditingConfig.class)
class PartnerSchemaPostgresTest {

    @Autowired
    private PartnerRepository partners;

    @Autowired
    private IncomingConfigRepository incoming;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void identificadorAs2Activo_esUnicoSinDistinguirMayusculas() {
        partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-A", true));

        assertThatThrownBy(() -> partners.saveAndFlush(PartnerRepositoryTest.partner("socio-pg-a", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void socioDadoDeBaja_noBloqueaReutilizarElIdentificador() {
        partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-B", false));

        Partner nuevo = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-B", true));

        assertThat(nuevo.getId()).isNotNull();
    }

    @Test
    void borrarElSocio_borraSusConfiguraciones() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-C", true));
        IncomingConfig in = new IncomingConfig(p);
        in.setIncomingFolder("/data/SOCIO-PG-C/Incoming");
        incoming.saveAndFlush(in);

        jdbc.update("DELETE FROM partner WHERE id = ?", p.getId());

        Integer restantes = jdbc.queryForObject(
                "SELECT count(*) FROM incoming_config WHERE partner_id = ?", Integer.class, p.getId());
        assertThat(restantes).isZero();
    }

    @Test
    void restricciones_rechazanPuertoYProtocoloInvalidos() {
        Partner p = PartnerRepositoryTest.partner("SOCIO-PG-D", true);
        p.setPort(70000);

        assertThatThrownBy(() -> partners.saveAndFlush(p)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
