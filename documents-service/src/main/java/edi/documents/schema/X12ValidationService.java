package edi.documents.schema;

import edi.documents.x12.X12Document;
import edi.documents.x12.X12Parser;
import edi.documents.x12.X12Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida un documento X12 contra el esquema de su tipo: que traiga los segmentos obligatorios y que
 * sus sobres cierren bien (conteos y numeros de control de SE, GE e IEA iguales a los de ST, GS e
 * ISA). Un documento con errores se informa, no se rechaza con excepcion: la consola muestra que falta.
 */
@Service
public class X12ValidationService {

    public enum Status {
        VALIDATED,
        VALIDATION_FAILED
    }

    /**
     * @param missingSegments segmentos obligatorios que no estan
     * @param problems        otros fallos (tipo sin esquema, sobres que no cierran)
     */
    public record ValidationResult(Status status, String typeCode, String version, List<String> missingSegments,
            List<String> problems) {
    }

    private final DocumentSchemaRepository schemas;

    public X12ValidationService(DocumentSchemaRepository schemas) {
        this.schemas = schemas;
    }

    @Transactional(readOnly = true)
    public List<DocumentSchema> schemas() {
        return schemas.findAll();
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(String text) {
        X12Document doc = X12Parser.parse(text);
        if (!doc.isKnown()) {
            return result(doc, List.of(), List.of("No se reconoce el documento: falta el sobre ISA o el segmento ST"));
        }
        Optional<DocumentSchema> schema = schemas.findById(doc.typeCode());
        if (schema.isEmpty()) {
            return result(doc, List.of(), List.of("No hay esquema para el tipo " + doc.typeCode()));
        }
        List<String> present = doc.segments().stream().map(X12Segment::id).toList();
        List<String> missing = schema.get().getRequiredSegments().stream().filter(s -> !present.contains(s)).toList();
        return result(doc, missing, envelopeProblems(doc));
    }

    /** Los cierres de cada sobre repiten el numero de control de su apertura y cuentan lo que contienen. */
    static List<String> envelopeProblems(X12Document doc) {
        List<String> problems = new ArrayList<>();
        List<X12Segment> segs = doc.segments();
        int st = indexOf(segs, "ST");
        int se = indexOf(segs, "SE");
        if (st >= 0 && se > st) {
            X12Segment seSeg = segs.get(se);
            int counted = se - st + 1;
            if (!sameNumber(seSeg.element(1), String.valueOf(counted))) {
                problems.add("SE01 declara " + seSeg.element(1) + " segmentos y la transaccion tiene " + counted);
            }
            if (!sameNumber(seSeg.element(2), doc.transactionControl())) {
                problems.add("SE02 (" + seSeg.element(2) + ") no coincide con ST02 (" + doc.transactionControl() + ")");
            }
        }
        doc.first("GE").ifPresent(ge -> {
            long transactions = segs.stream().filter(s -> s.id().equals("ST")).count();
            if (!sameNumber(ge.element(1), String.valueOf(transactions))) {
                problems.add("GE01 declara " + ge.element(1) + " transacciones y el grupo tiene " + transactions);
            }
            if (!sameNumber(ge.element(2), doc.groupControl())) {
                problems.add("GE02 (" + ge.element(2) + ") no coincide con GS06 (" + doc.groupControl() + ")");
            }
        });
        doc.first("IEA").ifPresent(iea -> {
            long groups = segs.stream().filter(s -> s.id().equals("GS")).count();
            if (!sameNumber(iea.element(1), String.valueOf(groups))) {
                problems.add("IEA01 declara " + iea.element(1) + " grupos y el intercambio tiene " + groups);
            }
            if (!sameNumber(iea.element(2), doc.interchangeControl())) {
                problems.add("IEA02 (" + iea.element(2) + ") no coincide con ISA13 (" + doc.interchangeControl() + ")");
            }
        });
        return problems;
    }

    private static ValidationResult result(X12Document doc, List<String> missing, List<String> problems) {
        Status status = missing.isEmpty() && problems.isEmpty() ? Status.VALIDATED : Status.VALIDATION_FAILED;
        return new ValidationResult(status, doc.typeCode(), doc.version(), missing, problems);
    }

    private static int indexOf(List<X12Segment> segs, String id) {
        for (int i = 0; i < segs.size(); i++) {
            if (segs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Numeros de control con o sin ceros a la izquierda ("000000001" y "1" son el mismo). */
    private static boolean sameNumber(String a, String b) {
        String x = a == null ? "" : a.strip();
        String y = b == null ? "" : b.strip();
        try {
            return Long.parseLong(x) == Long.parseLong(y);
        } catch (NumberFormatException _) {
            return x.equals(y);
        }
    }
}
