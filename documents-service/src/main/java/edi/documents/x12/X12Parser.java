package edi.documents.x12;

import edi.documents.x12.X12Document.Party;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Interpreta un documento ANSI X12 sin depender de librerias externas.
 *
 * <p>Los separadores no se suponen: se leen del ISA, que tiene ancho fijo (106 caracteres con su fin
 * de segmento). El cuarto caracter es el separador de elementos, el 105 el de componentes (ISA16) y
 * el 106 el fin de segmento. Asi el mismo codigo lee documentos con {@code ~}, con {@code ~} y salto
 * de linea, o con un salto de linea (LF o CRLF) como fin de segmento.</p>
 *
 * <p>Si el texto no empieza por un ISA valido no lanza excepcion: devuelve un documento
 * {@code UNKNOWN} con los segmentos que pudo separar, para mostrarlo igual en la consola.</p>
 */
public final class X12Parser {

    /** Longitud del ISA con su fin de segmento. */
    public static final int ISA_LENGTH = 106;
    private static final int COMPONENT_POSITION = 104;
    private static final int TERMINATOR_POSITION = 105;
    private static final int ISA_SEPARATORS = 16;

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");
    private static final Pattern SIMPLE_SPLIT = Pattern.compile("~\\s*|\\r\\n|\\r|\\n");
    private static final char BOM = '\uFEFF';

    private X12Parser() {
    }

    public static X12Document parse(String raw) {
        String text = raw == null ? "" : raw;
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        text = text.stripLeading();
        if (!text.startsWith("ISA") || text.length() < ISA_LENGTH) {
            return incomplete(text);
        }
        char element = text.charAt(3);
        if (count(text, element, COMPONENT_POSITION) != ISA_SEPARATORS) {
            return incomplete(text);
        }
        char component = text.charAt(COMPONENT_POSITION);
        char terminator = text.charAt(TERMINATOR_POSITION);
        String segmentText = terminator == '\r' && text.length() > ISA_LENGTH && text.charAt(ISA_LENGTH) == '\n'
                ? "\r\n" : String.valueOf(terminator);
        X12Delimiters delimiters = new X12Delimiters(element, component, segmentText);

        List<X12Segment> segments = new ArrayList<>();
        for (String s : rawSegments(text, terminator)) {
            segments.add(segment(s, element));
        }
        return build(segments, delimiters);
    }

    private static X12Document build(List<X12Segment> segments, X12Delimiters delimiters) {
        X12Segment isa = find(segments, "ISA");
        X12Segment gs = find(segments, "GS");
        X12Segment st = find(segments, "ST");
        String type = st.element(1).isBlank() ? X12Document.UNKNOWN : st.element(1).strip();
        boolean complete = segments.stream().map(X12Segment::id).toList()
                .containsAll(List.of("ISA", "GS", "ST", "SE", "GE", "IEA"));
        return new X12Document(
                type,
                version(gs.element(8), isa.element(12)),
                gs.element(1).strip(),
                new Party(isa.element(5).strip(), isa.element(6).strip()),
                new Party(isa.element(7).strip(), isa.element(8).strip()),
                gs.element(2).strip(),
                gs.element(3).strip(),
                isa.element(9).strip(),
                isa.element(10).strip(),
                isa.element(15).strip(),
                isa.element(13).strip(),
                gs.element(6).strip(),
                st.element(2).strip(),
                delimiters,
                segments,
                complete);
    }

    /**
     * Version corta (4010, 5010). GS08 la trae completa ({@code 004010}, {@code 005010X222A1});
     * ISA12 solo la del sobre ({@code 00401}).
     */
    static String version(String gs08, String isa12) {
        String g = leadingDigits(gs08);
        if (g.length() >= 6) {
            return g.substring(2, 6);
        }
        String i = leadingDigits(isa12);
        if (i.length() == 5) {
            return i.substring(2) + "0";
        }
        return X12Document.UNKNOWN;
    }

    private static X12Document incomplete(String text) {
        List<X12Segment> segments = new ArrayList<>();
        for (String part : SIMPLE_SPLIT.split(text)) {
            String s = part.strip();
            if (!s.isEmpty()) {
                segments.add(segment(s, X12Delimiters.UNKNOWN.element()));
            }
        }
        return new X12Document(X12Document.UNKNOWN, X12Document.UNKNOWN, "", Party.NONE, Party.NONE, "", "",
                "", "", "", "", "", "", X12Delimiters.UNKNOWN, segments, false);
    }

    private static List<String> rawSegments(String text, char terminator) {
        String[] parts = terminator == '\r' || terminator == '\n'
                ? LINE_BREAK.split(text)
                : text.split(Pattern.quote(String.valueOf(terminator)), -1);
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = trimLineBreaks(p);
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Quita los saltos de linea de los extremos recorriendo el texto, sin expresion regular: una como
     * {@code [\r\n]+$} tarda de forma cuadratica con muchos saltos seguidos, y el texto viene de fuera.
     */
    static String trimLineBreaks(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isLineBreak(s.charAt(start))) {
            start++;
        }
        while (end > start && isLineBreak(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isLineBreak(char c) {
        return c == '\r' || c == '\n';
    }

    private static X12Segment segment(String text, char element) {
        String[] parts = text.split(Pattern.quote(String.valueOf(element)), -1);
        List<String> elements = new ArrayList<>(parts.length);
        for (int i = 1; i < parts.length; i++) {
            elements.add(parts[i]);
        }
        return new X12Segment(parts[0].strip(), elements);
    }

    private static X12Segment find(List<X12Segment> segments, String id) {
        return segments.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElse(new X12Segment(id, List.of()));
    }

    private static int count(String text, char c, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static String leadingDigits(String s) {
        String v = s == null ? "" : s.strip();
        int i = 0;
        while (i < v.length() && Character.isDigit(v.charAt(i))) {
            i++;
        }
        return v.substring(0, i);
    }
}
