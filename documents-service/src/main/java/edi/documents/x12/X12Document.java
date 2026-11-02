package edi.documents.x12;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Un documento X12 interpretado: tipo, version, partes con sus calificadores y numeros de control de
 * los tres sobres (ISA intercambio, GS grupo funcional, ST transaccion).
 *
 * <p>Un texto que no empieza por un ISA valido se devuelve con {@code typeCode = UNKNOWN} y
 * {@code complete = false}, sin excepcion: quien lo recibe decide que hacer con el.</p>
 *
 * @param typeCode           ST01: 850, 997, 889... o {@code UNKNOWN}
 * @param version            4010, 5010... (de GS08 o, si falta, de ISA12) o {@code UNKNOWN}
 * @param functionalCode     GS01: PO, FA...
 * @param sender             ISA05 calificador e ISA06 identificador del emisor
 * @param receiver           ISA07 calificador e ISA08 identificador del receptor
 * @param groupSender        GS02
 * @param groupReceiver      GS03
 * @param interchangeDate    ISA09 (AAMMDD)
 * @param interchangeTime    ISA10 (HHMM)
 * @param usageIndicator     ISA15: P produccion, T pruebas
 * @param interchangeControl ISA13
 * @param groupControl       GS06
 * @param transactionControl ST02
 * @param delimiters         separadores leidos del ISA
 * @param segments           todos los segmentos, en orden
 * @param complete           trae los seis sobres (ISA, GS, ST, SE, GE, IEA)
 */
public record X12Document(
        String typeCode,
        String version,
        String functionalCode,
        Party sender,
        Party receiver,
        String groupSender,
        String groupReceiver,
        String interchangeDate,
        String interchangeTime,
        String usageIndicator,
        String interchangeControl,
        String groupControl,
        String transactionControl,
        X12Delimiters delimiters,
        List<X12Segment> segments,
        boolean complete) {

    public static final String UNKNOWN = "UNKNOWN";

    /** Una parte del intercambio: calificador del tipo de identificador (01, 08, 12, ZZ...) e identificador. */
    public record Party(String qualifier, String id) {
        public static final Party NONE = new Party("", "");
    }

    public X12Document {
        segments = List.copyOf(segments);
    }

    public boolean isKnown() {
        return !UNKNOWN.equals(typeCode);
    }

    /** Primer segmento con ese identificador. */
    public Optional<X12Segment> first(String id) {
        return segments.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public boolean has(String id) {
        return first(id).isPresent();
    }

    /** Numeros de control para indexar y detectar duplicados. */
    public Map<String, String> controls() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ISA13", interchangeControl);
        m.put("GS06", groupControl);
        m.put("ST02", transactionControl);
        return m;
    }
}
