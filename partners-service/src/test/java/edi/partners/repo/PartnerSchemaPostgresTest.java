package edi.partners.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.partners.domain.IncomingConfig;
import edi.partners.domain.KeyUsage;
import edi.partners.domain.PartnerCertificate;
import edi.partners.domain.Partner;
import edi.partners.testsupport.EnabledIfPostgres;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Esquema real: Flyway aplica las migraciones sobre PostgreSQL y Hibernate lo valida contra las
 * entidades ({@code ddl-auto=validate}). Comprueba lo que H2 no puede: el indice unico parcial del
 * identificador AS2, el borrado en cascada de las configuraciones y que la bitacora sea de solo
 * insercion.
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

    @Test
    void etapaDeCarpetas_porDefectoLocal_ySoloValoresConocidos() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-F", true));

        assertThat(jdbc.queryForObject("SELECT folder_state FROM partner WHERE id = ?", String.class, p.getId()))
                .isEqualTo("LOCAL");
        assertThatThrownBy(() -> jdbc.update("UPDATE partner SET folder_state = 'OTRO' WHERE id = ?", p.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Un evento de la bitacora del socio. Cada prueba siguiente provoca un solo error: tras el primero,
     * PostgreSQL anula la transaccion de la prueba y rechaza cualquier otra sentencia.
     */
    private java.util.UUID evento(String as2Name) {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner(as2Name, true));
        java.util.UUID id = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, event_type, status, actor, source, partner_id, partner_as2_name)
                VALUES (?, now(), 'PARTNER_CREATED', 'SUCCESS', 'operador1', 'partners-service', ?, ?)
                """, id, p.getId(), as2Name);
        return id;
    }

    @Test
    void bitacora_noSePuedeModificar() {
        java.util.UUID id = evento("SOCIO-PG-E1");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET actor = 'otro' WHERE id = ?", id))
                .hasMessageContaining("solo insercion");
    }

    @Test
    void bitacora_noSePuedeBorrar() {
        java.util.UUID id = evento("SOCIO-PG-E2");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE id = ?", id))
                .hasMessageContaining("solo insercion");
    }

    @Test
    void bitacora_conservaElEventoAunqueSeBorreElSocio() {
        java.util.UUID id = evento("SOCIO-PG-E3");

        jdbc.update("DELETE FROM partner WHERE as2_name = 'SOCIO-PG-E3'");

        assertThat(jdbc.queryForObject("SELECT partner_as2_name FROM audit_log WHERE id = ?", String.class, id))
                .isEqualTo("SOCIO-PG-E3");
    }

    @Test
    void bitacora_rechazaTiposDeEventoFueraDeFormato() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, event_type, status, actor, source)
                VALUES (gen_random_uuid(), now(), 'documento enviado', 'SUCCESS', 'system', 'x')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bitacora_rechazaEstadosDesconocidos() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO audit_log (id, occurred_at, event_type, status, actor, source)
                VALUES (gen_random_uuid(), now(), 'DOCUMENT_SENT', 'QUIZAS', 'system', 'x')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ certificados (HU-11)

    @Autowired
    private PartnerCertificateRepository certificates;

    private PartnerCertificate certificado(Partner p, String fingerprintChar, boolean preferred) {
        PartnerCertificate c = new PartnerCertificate(p, KeyUsage.ENCRYPTION, new PartnerCertificate.CertificateData(
                "socio", "socio", "CN=socio", "CN=socio", "1F", fingerprintChar.repeat(64),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"),
                "-----BEGIN CERTIFICATE-----\nAA==\n-----END CERTIFICATE-----\n"));
        c.setPreferred(preferred);
        return c;
    }

    @Test
    void certificados_unSoloPreferentePorSocioYUso() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-K1", true));
        certificates.saveAndFlush(certificado(p, "a", true));

        assertThatThrownBy(() -> certificates.saveAndFlush(certificado(p, "b", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificados_variosActivosDelMismoUso_sePermiten() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-K2", true));
        certificates.saveAndFlush(certificado(p, "a", true));
        certificates.saveAndFlush(certificado(p, "b", false));

        assertThat(certificates.findAllByPartnerIdAndKeyUsageAndActiveTrue(p.getId(), KeyUsage.ENCRYPTION)).hasSize(2);
    }

    @Test
    void certificados_huellaRepetidaParaElMismoUso_seRechaza() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-K3", true));
        certificates.saveAndFlush(certificado(p, "a", true));

        assertThatThrownBy(() -> certificates.saveAndFlush(certificado(p, "a", false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificados_preferenteInactivo_seRechaza() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-K4", true));
        PartnerCertificate c = certificates.saveAndFlush(certificado(p, "a", false));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE partner_certificate SET active = false, preferred = true WHERE id = ?", c.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificados_seBorranConElSocio() {
        Partner p = partners.saveAndFlush(PartnerRepositoryTest.partner("SOCIO-PG-K5", true));
        certificates.saveAndFlush(certificado(p, "a", true));

        jdbc.update("DELETE FROM partner WHERE id = ?", p.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_certificate WHERE partner_id = ?",
                Integer.class, p.getId())).isZero();
    }
}
