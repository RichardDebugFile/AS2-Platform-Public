package edi.partners.svc.audit;

import static org.assertj.core.api.Assertions.assertThat;

import edi.partners.domain.AuditStatus;
import edi.partners.dto.AuditLogResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCsvTest {

    @Test
    void cabeceraConBom_unaFilaPorEvento_yCambiosEnJson() {
        var e = new AuditLogResponse(UUID.randomUUID(), Instant.parse("2026-10-20T15:30:00Z"), "PARTNER_UPDATED",
                AuditStatus.SUCCESS, "operador1", "partners-service", UUID.randomUUID(), "SOCIO-A", null,
                "Socio actualizado, con coma", Map.of("port", Map.of("from", 443, "to", 8443)));

        String csv = AuditCsv.write(List.of(e));

        assertThat(csv).startsWith("﻿" + AuditCsv.HEADER + "\r\n");
        assertThat(csv).contains("2026-10-20T15:30:00Z,PARTNER_UPDATED,SUCCESS,operador1,partners-service,SOCIO-A,,"
                + "\"Socio actualizado, con coma\",\"{\"\"port\"\":");
        assertThat(csv.split("\r\n")).hasSize(2);
    }

    @Test
    void celdas_comillasEscapadas_ySinFormulasDeHojaDeCalculo() {
        assertThat(AuditCsv.cell("dice \"hola\"")).isEqualTo("\"dice \"\"hola\"\"\"");
        assertThat(AuditCsv.cell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(AuditCsv.cell("+1")).isEqualTo("'+1");
        assertThat(AuditCsv.cell("-5")).isEqualTo("'-5");
        assertThat(AuditCsv.cell("@SUM")).isEqualTo("'@SUM");
        assertThat(AuditCsv.cell("linea\nnueva")).isEqualTo("\"linea\nnueva\"");
        assertThat(AuditCsv.cell(null)).isEmpty();
        assertThat(AuditCsv.cell("normal")).isEqualTo("normal");
    }
}
