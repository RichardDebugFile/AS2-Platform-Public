package edi.documents.x12;

import edi.documents.x12.X12Document.Party;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera documentos X12 validos: orden de compra 850, acuse funcional 997 y anuncio de promocion 889.
 *
 * <p>Se ocupa de lo que suele fallar al escribirlos a mano: el ISA de ancho fijo (identificadores
 * rellenados a 15 caracteres, 106 caracteres en total), la fecha y la hora del intercambio y los
 * conteos de cierre (SE01 segmentos de la transaccion, GE01 transacciones del grupo, IEA01 grupos del
 * intercambio). Cada segmento termina en {@code ~} seguido de un salto de linea para que se lea bien.</p>
 */
public final class X12Generator {

    public static final String TERMINATOR = "~\n";
    private static final char SEPARATOR = '*';
    private static final char COMPONENT = '>';
    private static final DateTimeFormatter ISA_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MAX_CONTROL = 999_999_999L;

    private X12Generator() {
    }

    /**
     * Sobres del intercambio.
     *
     * @param sender        ISA05/ISA06
     * @param receiver      ISA07/ISA08
     * @param groupSender   GS02
     * @param groupReceiver GS03
     * @param version       4010 o 5010
     * @param production    ISA15 = P (si no, T: pruebas)
     * @param controlNumber ISA13 y GS06; ST02 con al menos 4 digitos
     * @param at            fecha y hora del intercambio
     */
    public record Envelope(Party sender, Party receiver, String groupSender, String groupReceiver, String version,
            boolean production, long controlNumber, LocalDateTime at) {

        public static Envelope of(Party sender, Party receiver, String version, boolean production, long controlNumber,
                LocalDateTime at) {
            return new Envelope(sender, receiver, sender.id(), receiver.id(), version, production, controlNumber, at);
        }
    }

    /** Empresa con su nombre e identificador (N1). */
    public record Company(String name, String id) {
    }

    /** Linea de una orden de compra (PO1 y su descripcion PID). */
    public record Line(BigDecimal quantity, String unit, BigDecimal unitPrice, String productId, String description) {
    }

    /** Datos de una orden de compra 850. */
    public record PurchaseOrder(String number, LocalDate date, String purpose, String type, String reference,
            LocalDate deliveryDate, Company buyer, Company seller, List<Line> lines) {
    }

    /** Datos de un anuncio de promocion 889. */
    public record Promotion(String id, LocalDate date, LocalDate start, LocalDate end, Company vendor) {
    }

    /** Orden de compra 850. */
    public static String purchaseOrder(Envelope env, PurchaseOrder po) {
        if (po.lines() == null || po.lines().isEmpty()) {
            throw new IllegalArgumentException("La orden de compra necesita al menos una linea");
        }
        List<String> body = new ArrayList<>();
        body.add(seg("BEG", field("BEG01", po.purpose()), field("BEG02", po.type()), field("BEG03", po.number()), "",
                po.date().format(DATE)));
        if (po.reference() != null && !po.reference().isBlank()) {
            body.add(seg("REF", "DP", field("REF02", po.reference())));
        }
        body.add(seg("DTM", "002", po.deliveryDate().format(DATE)));
        body.add(seg("N1", "BY", field("N102", po.buyer().name()), "92", field("N104", po.buyer().id())));
        body.add(seg("N1", "SE", field("N102", po.seller().name()), "92", field("N104", po.seller().id())));
        int n = 1;
        for (Line line : po.lines()) {
            body.add(seg("PO1", String.valueOf(n++), number(line.quantity()), field("PO103", line.unit()),
                    number(line.unitPrice()), "", "VP", field("PO107", line.productId())));
            if (line.description() != null && !line.description().isBlank()) {
                body.add(seg("PID", "F", "", "", "", field("PID05", line.description())));
            }
        }
        body.add(seg("CTT", String.valueOf(po.lines().size())));
        return wrap(env, "850", body);
    }

    /**
     * Acuse funcional 997 de un documento recibido: lo envia quien lo recibio, asi que emisor y receptor
     * (ISA y GS) van invertidos respecto del original. AK1 cita el grupo original (GS01 y GS06) y AK2
     * la transaccion (ST01 y ST02).
     */
    public static String functionalAck(X12Document original, boolean production, long controlNumber,
            LocalDateTime at) {
        if (!original.isKnown() || original.groupControl().isBlank() || original.transactionControl().isBlank()) {
            throw new IllegalArgumentException("El documento original no se pudo interpretar: no se puede acusar");
        }
        Envelope env = new Envelope(original.receiver(), original.sender(), original.groupReceiver(),
                original.groupSender(), original.version(), production, controlNumber, at);
        List<String> body = List.of(
                seg("AK1", original.functionalCode(), original.groupControl()),
                seg("AK2", original.typeCode(), original.transactionControl()),
                seg("AK5", "A"),
                seg("AK9", "A", "1", "1", "1"));
        return wrap(env, "997", body);
    }

    /** Anuncio de promocion 889: vigencia de la promocion y proveedor. */
    public static String promotion(Envelope env, Promotion p) {
        if (p.end().isBefore(p.start())) {
            throw new IllegalArgumentException("La promocion termina antes de empezar");
        }
        List<String> body = List.of(
                seg("BSC", "PA", field("BSC02", p.id()), p.date().format(DATE)),
                seg("DTM", "194", p.start().format(DATE)),
                seg("DTM", "195", p.end().format(DATE)),
                seg("N1", "VN", field("N102", p.vendor().name()), "92", field("N104", p.vendor().id())));
        return wrap(env, "889", body);
    }

    /** Codigo del grupo funcional (GS01) de cada tipo. */
    static String functionalCode(String type) {
        return switch (type) {
            case "850" -> "PO";
            case "997" -> "FA";
            case "889" -> "PA";
            default -> throw new IllegalArgumentException("Tipo no admitido: " + type);
        };
    }

    private static String wrap(Envelope env, String type, List<String> body) {
        if (env.controlNumber() < 1 || env.controlNumber() > MAX_CONTROL) {
            throw new IllegalArgumentException("El numero de control va de 1 a 999999999");
        }
        boolean v5010 = switch (env.version() == null ? "4010" : env.version()) {
            case "4010" -> false;
            case "5010" -> true;
            default -> throw new IllegalArgumentException("Version no admitida: " + env.version() + " (4010 o 5010)");
        };
        String control9 = String.format("%09d", env.controlNumber());
        String group = String.valueOf(env.controlNumber());
        String transaction = String.format("%04d", env.controlNumber());

        List<String> out = new ArrayList<>();
        out.add(isa(env, v5010, control9));
        out.add(seg("GS", functionalCode(type), field("GS02", env.groupSender()), field("GS03", env.groupReceiver()),
                env.at().format(DATE), env.at().format(TIME), group, "X", v5010 ? "005010" : "004010"));
        out.add(seg("ST", type, transaction));
        out.addAll(body);
        out.add(seg("SE", String.valueOf(body.size() + 2), transaction));
        out.add(seg("GE", "1", group));
        out.add(seg("IEA", "1", control9));
        return String.join(TERMINATOR, out) + TERMINATOR;
    }

    /** ISA de ancho fijo: cada elemento tiene su longitud exacta, de ahi los 106 caracteres. */
    private static String isa(Envelope env, boolean v5010, String control9) {
        return seg("ISA", "00", " ".repeat(10), "00", " ".repeat(10),
                qualifier("ISA05", env.sender()), pad("ISA06", env.sender().id()),
                qualifier("ISA07", env.receiver()), pad("ISA08", env.receiver().id()),
                env.at().format(ISA_DATE), env.at().format(TIME),
                v5010 ? "^" : "U", v5010 ? "00501" : "00401", control9, "0",
                env.production() ? "P" : "T", String.valueOf(COMPONENT));
    }

    private static String qualifier(String name, Party p) {
        String q = field(name, p.qualifier());
        if (q.length() != 2) {
            throw new IllegalArgumentException(name + " debe tener 2 caracteres (01, 08, 12, ZZ...)");
        }
        return q;
    }

    private static String pad(String name, String id) {
        String v = field(name, id);
        if (v.isEmpty() || v.length() > 15) {
            throw new IllegalArgumentException(name + " debe tener entre 1 y 15 caracteres");
        }
        return v + " ".repeat(15 - v.length());
    }

    /** Un valor no puede llevar los separadores: romperia la estructura del documento. */
    private static String field(String name, String value) {
        String v = value == null ? "" : value.strip();
        for (char c : v.toCharArray()) {
            if (c == SEPARATOR || c == COMPONENT || c == '~' || c == '\n' || c == '\r') {
                throw new IllegalArgumentException(name + " no puede contener '" + c + "'");
            }
        }
        return v;
    }

    private static String number(BigDecimal n) {
        if (n == null || n.signum() < 0) {
            throw new IllegalArgumentException("Cantidad o precio no valido");
        }
        return n.stripTrailingZeros().toPlainString();
    }

    private static String seg(String id, String... elements) {
        return id + SEPARATOR + String.join(String.valueOf(SEPARATOR), elements);
    }
}
