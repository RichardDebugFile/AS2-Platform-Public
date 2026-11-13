package edi.documents.query;

import static org.assertj.core.api.Assertions.assertThat;

import edi.documents.query.Dashboard.Outcome;
import edi.documents.store.Direction;
import edi.documents.store.DocumentRepository.DashboardRow;
import edi.documents.store.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 8);
    private static final LocalDate TO = LocalDate.of(2026, 11, 10);

    private record Row(String partnerAs2Name, Direction direction, DocumentStatus status, Instant createdAt)
            implements DashboardRow {
        @Override
        public String getPartnerAs2Name() {
            return partnerAs2Name;
        }

        @Override
        public Direction getDirection() {
            return direction;
        }

        @Override
        public DocumentStatus getStatus() {
            return status;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    /** Hora local de Guayaquil del dia indicado. */
    private static Instant at(int day, int hour) {
        return LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(ZONE).toInstant();
    }

    @Test
    void clasificaCadaEstadoEnUnResultado() {
        assertThat(Dashboard.outcome(DocumentStatus.MDN_RECEIVED)).isEqualTo(Outcome.OK);
        assertThat(Dashboard.outcome(DocumentStatus.DELIVERED)).isEqualTo(Outcome.OK);
        assertThat(Dashboard.outcome(DocumentStatus.SEND_FAILED)).isEqualTo(Outcome.FAILED);
        assertThat(Dashboard.outcome(DocumentStatus.PROCESSING_FAILED)).isEqualTo(Outcome.FAILED);
        assertThat(Dashboard.outcome(DocumentStatus.CREATED)).isEqualTo(Outcome.IN_PROGRESS);
        assertThat(Dashboard.outcome(DocumentStatus.MDN_PENDING)).isEqualTo(Outcome.IN_PROGRESS);
        assertThat(Dashboard.outcome(DocumentStatus.DUPLICATE)).isEqualTo(Outcome.DUPLICATE);
    }

    @Test
    void sumaPorResultadoSocioYDia() {
        List<DashboardRow> rows = List.of(
                new Row("SOCIO-A", Direction.OUTBOUND, DocumentStatus.MDN_RECEIVED, at(8, 9)),
                new Row("socio-a", Direction.INBOUND, DocumentStatus.VALIDATED, at(8, 23)),
                new Row("SOCIO-A", Direction.OUTBOUND, DocumentStatus.SEND_FAILED, at(10, 1)),
                new Row("SOCIO-B", Direction.INBOUND, DocumentStatus.RECEIVED, at(10, 12)),
                new Row("SOCIO-B", Direction.INBOUND, DocumentStatus.DUPLICATE, at(10, 13)));

        Dashboard d = Dashboard.of(FROM, TO, rows, ZONE);

        assertThat(d.total()).isEqualTo(5);
        assertThat(d.ok()).isEqualTo(2);
        assertThat(d.failed()).isEqualTo(1);
        assertThat(d.inProgress()).isEqualTo(1);
        assertThat(d.duplicates()).isEqualTo(1);
        assertThat(d.successRate()).isEqualTo(66.7);
        assertThat(d.byDirection()).containsEntry("OUTBOUND", 2L).containsEntry("INBOUND", 3L);
        assertThat(d.byStatus()).containsEntry("MDN_RECEIVED", 1L).containsEntry("DUPLICATE", 1L);

        // El socio no distingue mayusculas; el de mas trafico va primero.
        assertThat(d.byPartner()).containsExactly(
                new Dashboard.PartnerTraffic("SOCIO-A", 1, 2, 2, 1, 3),
                new Dashboard.PartnerTraffic("SOCIO-B", 2, 0, 0, 0, 2));

        // Un documento a las 23:00 de Guayaquil cuenta en ese dia aunque en UTC ya sea el siguiente.
        assertThat(d.byDay()).containsExactly(
                new Dashboard.DayTraffic(LocalDate.of(2026, 11, 8), 2, 0, 0, 2),
                new Dashboard.DayTraffic(LocalDate.of(2026, 11, 9), 0, 0, 0, 0),
                new Dashboard.DayTraffic(LocalDate.of(2026, 11, 10), 0, 1, 2, 3));
    }

    @Test
    void sinResultadosFinalesNoHayPorcentaje() {
        Dashboard d = Dashboard.of(FROM, TO,
                List.of(new Row("SOCIO-A", Direction.OUTBOUND, DocumentStatus.CREATED, at(9, 10))), ZONE);

        assertThat(d.successRate()).isNull();
        assertThat(d.inProgress()).isEqualTo(1);
        assertThat(Dashboard.of(FROM, TO, List.of(), ZONE).byDay()).hasSize(3).allMatch(x -> x.total() == 0);
    }
}
