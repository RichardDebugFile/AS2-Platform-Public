package edi.documents.query;

import edi.documents.store.Direction;
import edi.documents.store.DocumentRepository.DashboardRow;
import edi.documents.store.DocumentStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cifras del tablero de Inicio (HU-20, adelanto AS2-99) para un rango de fechas: totales por resultado,
 * por estado y direccion, trafico por socio y documentos por dia.
 *
 * @param from       primer dia del rango (incluido)
 * @param to         ultimo dia del rango (incluido)
 * @param total      documentos en el rango
 * @param ok         procesados correctamente (MDN recibido, validados o entregados)
 * @param failed     con error en algun paso
 * @param inProgress aun sin resultado final (creados, recibidos, enviados o esperando MDN)
 * @param duplicates reintentos del socio detectados como repetidos
 * @param successRate porcentaje de correctos sobre los que ya tienen resultado; nulo si ninguno lo tiene
 */
public record Dashboard(LocalDate from, LocalDate to, long total, long ok, long failed, long inProgress,
        long duplicates, Double successRate, Map<String, Long> byStatus, Map<String, Long> byDirection,
        List<PartnerTraffic> byPartner, List<DayTraffic> byDay) {

    /** Resultado de un documento segun su estado. */
    public enum Outcome { OK, FAILED, IN_PROGRESS, DUPLICATE }

    static final Set<DocumentStatus> OK = EnumSet.of(DocumentStatus.MDN_RECEIVED, DocumentStatus.VALIDATED,
            DocumentStatus.DELIVERED);
    static final Set<DocumentStatus> FAILED = EnumSet.of(DocumentStatus.SEND_FAILED, DocumentStatus.MDN_FAILED,
            DocumentStatus.VALIDATION_FAILED, DocumentStatus.DELIVERY_FAILED, DocumentStatus.PROCESSING_FAILED);

    public static Outcome outcome(DocumentStatus s) {
        if (s == DocumentStatus.DUPLICATE) return Outcome.DUPLICATE;
        if (OK.contains(s)) return Outcome.OK;
        if (FAILED.contains(s)) return Outcome.FAILED;
        return Outcome.IN_PROGRESS;
    }

    /** Trafico de un socio en el rango. */
    public record PartnerTraffic(String partner, long inbound, long outbound, long ok, long failed, long total) {
    }

    /** Documentos de un dia, separados por resultado. */
    public record DayTraffic(LocalDate day, long ok, long failed, long other, long total) {
    }

    private static int dayColumn(Outcome o) {
        return switch (o) {
            case OK -> 0;
            case FAILED -> 1;
            default -> 2;
        };
    }

    /** Agrega las filas del rango; los dias sin documentos aparecen con cero. */
    static Dashboard of(LocalDate from, LocalDate to, List<DashboardRow> rows, ZoneId zone) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byDirection = new LinkedHashMap<>();
        Map<String, long[]> partners = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<LocalDate, long[]> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new long[3]);
        }
        long[] totals = new long[Outcome.values().length];
        for (DashboardRow r : rows) {
            Outcome o = outcome(r.getStatus());
            totals[o.ordinal()]++;
            byStatus.merge(r.getStatus().name(), 1L, Long::sum);
            byDirection.merge(r.getDirection().name(), 1L, Long::sum);
            long[] p = partners.computeIfAbsent(r.getPartnerAs2Name(), k -> new long[4]);
            p[r.getDirection() == Direction.INBOUND ? 0 : 1]++;
            if (o == Outcome.OK) p[2]++;
            if (o == Outcome.FAILED) p[3]++;
            long[] day = days.get(r.getCreatedAt().atZone(zone).toLocalDate());
            if (day != null) day[dayColumn(o)]++;
        }
        List<PartnerTraffic> byPartner = new ArrayList<>();
        partners.forEach((name, p) -> byPartner.add(new PartnerTraffic(name, p[0], p[1], p[2], p[3], p[0] + p[1])));
        byPartner.sort(Comparator.comparingLong(PartnerTraffic::total).reversed());
        List<DayTraffic> byDay = new ArrayList<>();
        days.forEach((d, n) -> byDay.add(new DayTraffic(d, n[0], n[1], n[2], n[0] + n[1] + n[2])));
        long ok = totals[Outcome.OK.ordinal()];
        long failed = totals[Outcome.FAILED.ordinal()];
        Double rate = ok + failed == 0 ? null : Math.round(1000.0 * ok / (ok + failed)) / 10.0;
        return new Dashboard(from, to, rows.size(), ok, failed, totals[Outcome.IN_PROGRESS.ordinal()],
                totals[Outcome.DUPLICATE.ordinal()], rate, byStatus, byDirection, byPartner, byDay);
    }
}
