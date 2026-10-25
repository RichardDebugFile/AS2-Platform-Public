package edi.partners.svc.audit;

import edi.partners.dto.AuditLogResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** Exportacion de la bitacora a CSV (UTF-8 con BOM, para que Excel respete los acentos). */
public final class AuditCsv {

    /** Marca de orden de bytes UTF-8: Excel la usa para leer bien los acentos. */
    static final char BOM = 0xFEFF;
    static final String HEADER = "fecha_utc,evento,estado,usuario,origen,socio,message_id,detalle,cambios";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AuditCsv() {
    }

    public static String write(List<AuditLogResponse> rows) {
        StringBuilder out = new StringBuilder().append(BOM).append(HEADER).append("\r\n");
        for (AuditLogResponse r : rows) {
            out.append(List.of(Objects.toString(r.occurredAt(), ""), r.eventType(), r.status().name(), r.actor(),
                            r.source(), Objects.toString(r.partnerAs2Name(), ""), Objects.toString(r.messageId(), ""),
                            Objects.toString(r.detail(), ""), changes(r.changes()))
                    .stream().map(AuditCsv::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        return out.toString();
    }

    private static String changes(Map<String, Object> changes) {
        return changes.isEmpty() ? "" : JSON.writeValueAsString(changes);
    }

    /**
     * Celda CSV: entre comillas si hace falta. Un texto que empieza por = + - @ (o tabulador) se
     * antepone con ' para que una hoja de calculo no lo ejecute como formula.
     */
    static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        boolean quote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        return quote ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
