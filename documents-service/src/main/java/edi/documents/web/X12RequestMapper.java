package edi.documents.web;

import edi.documents.x12.X12Document;
import edi.documents.x12.X12Generator;
import edi.documents.x12.X12Generator.Company;
import edi.documents.x12.X12Generator.Envelope;
import edi.documents.x12.X12Generator.Line;
import edi.documents.x12.X12Parser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Convierte una {@link GenerateRequest} en el documento X12, completando los valores por defecto.
 * El reloj se inyecta para que las pruebas generen siempre el mismo documento.
 */
@Component
public class X12RequestMapper {

    public static final Set<String> SUPPORTED = Set.of("850", "997", "889");
    static final String DEFAULT_VERSION = "4010";
    static final int DEFAULT_DELIVERY_DAYS = 7;
    static final int DEFAULT_PROMOTION_DAYS = 30;
    private static final long MAX_CONTROL = 999_999_999L;

    private final Clock clock;

    public X12RequestMapper(Clock clock) {
        this.clock = clock;
    }

    public String generate(GenerateRequest r) {
        String type = r.type() == null ? "" : r.type().strip();
        if (!SUPPORTED.contains(type)) {
            throw new UnsupportedDocumentTypeException(type);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean production = Boolean.TRUE.equals(r.production());
        long control = r.controlNumber() != null ? r.controlNumber() : defaultControl(now);
        return switch (type) {
            case "850" -> X12Generator.purchaseOrder(envelope(r, production, control, now), purchaseOrder(r, now));
            case "889" -> X12Generator.promotion(envelope(r, production, control, now), promotion(r, now));
            default -> X12Generator.functionalAck(original(r), production, control, now);
        };
    }

    private static Envelope envelope(GenerateRequest r, boolean production, long control, LocalDateTime now) {
        String version = r.version() == null || r.version().isBlank() ? DEFAULT_VERSION : r.version().strip();
        return Envelope.of(party("sender", r.sender()), party("receiver", r.receiver()), version, production, control,
                now);
    }

    private static X12Generator.PurchaseOrder purchaseOrder(GenerateRequest r, LocalDateTime now) {
        GenerateRequest.PurchaseOrder po = require(r.purchaseOrder(), "purchaseOrder");
        LocalDate date = po.date() != null ? po.date() : now.toLocalDate();
        LocalDate delivery = po.deliveryDate() != null ? po.deliveryDate()
                : date.plusDays(po.deliveryDays() != null ? po.deliveryDays() : DEFAULT_DELIVERY_DAYS);
        List<Line> lines = require(po.lines(), "purchaseOrder.lines").stream()
                .map(l -> new Line(l.quantity(), l.unit() == null || l.unit().isBlank() ? "EA" : l.unit(),
                        l.unitPrice(), l.productId(), l.description()))
                .toList();
        return new X12Generator.PurchaseOrder(required(po.number(), "purchaseOrder.number"), date,
                po.purpose() == null ? "00" : po.purpose(), po.type() == null ? "SA" : po.type(), po.reference(),
                delivery, company(po.buyer(), "purchaseOrder.buyer"), company(po.seller(), "purchaseOrder.seller"),
                lines);
    }

    private static X12Generator.Promotion promotion(GenerateRequest r, LocalDateTime now) {
        GenerateRequest.Promotion p = require(r.promotion(), "promotion");
        LocalDate start = p.start() != null ? p.start() : now.toLocalDate();
        LocalDate end = p.end() != null ? p.end()
                : start.plusDays(p.days() != null ? p.days() : DEFAULT_PROMOTION_DAYS);
        return new X12Generator.Promotion(required(p.id(), "promotion.id"), now.toLocalDate(), start, end,
                company(p.vendor(), "promotion.vendor"));
    }

    private static X12Document original(GenerateRequest r) {
        return X12Parser.parse(required(r.original(), "original"));
    }

    /** Numero de control por defecto: segundos del dia mas el dia del anio, siempre entre 1 y 999999999. */
    static long defaultControl(LocalDateTime now) {
        long n = now.getDayOfYear() * 100_000L + now.toLocalTime().toSecondOfDay();
        return Math.max(1, n % MAX_CONTROL);
    }

    private static X12Document.Party party(String name, GenerateRequest.Party p) {
        GenerateRequest.Party v = require(p, name);
        return new X12Document.Party(required(v.qualifier(), name + ".qualifier"), required(v.id(), name + ".id"));
    }

    private static Company company(GenerateRequest.Company c, String name) {
        GenerateRequest.Company v = require(c, name);
        return new Company(required(v.name(), name + ".name"), required(v.id(), name + ".id"));
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Falta " + name);
        }
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta " + name);
        }
        return value.strip();
    }
}
