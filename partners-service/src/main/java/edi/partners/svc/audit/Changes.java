package edi.partners.svc.audit;

import edi.partners.svc.audit.AuditEvent.Change;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Diferencia campo a campo entre dos fotos (records) de lo mismo, para la bitacora. */
public final class Changes {

    /** Campos tecnicos que no son un cambio del usuario. */
    private static final Set<String> TECHNICAL =
            Set.of("id", "partnerId", "createdBy", "createdAt", "updatedBy", "updatedAt");

    private Changes() {
    }

    /**
     * Campos cuyo valor cambio, en el orden del record. Con {@code before} nulo (alta) se listan
     * todos los que tienen valor. Los enumerados se guardan por su nombre.
     */
    public static <T extends Record> Map<String, Change> between(T before, T after) {
        Map<String, Change> out = new LinkedHashMap<>();
        for (RecordComponent c : after.getClass().getRecordComponents()) {
            if (TECHNICAL.contains(c.getName())) {
                continue;
            }
            Object from = before == null ? null : plain(read(c, before));
            Object to = plain(read(c, after));
            if (!Objects.equals(from, to)) {
                out.put(c.getName(), new Change(from, to));
            }
        }
        return out;
    }

    private static Object read(RecordComponent c, Record r) {
        try {
            return c.getAccessor().invoke(r);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("No se pudo leer " + c.getName(), e);
        }
    }

    private static Object plain(Object v) {
        return v instanceof Enum<?> e ? e.name() : v;
    }
}
