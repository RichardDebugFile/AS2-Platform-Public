package edi.documents.x12;

import java.util.List;

/**
 * Un segmento X12: su identificador ({@code ISA}, {@code BEG}, {@code PO1}...) y sus elementos.
 * Los elementos se numeran como en el estandar: {@code element(6)} del ISA es ISA06.
 */
public record X12Segment(String id, List<String> elements) {

    public X12Segment {
        elements = List.copyOf(elements);
    }

    /** Elemento por su posicion X12 (desde 1); vacio si el segmento no lo trae. */
    public String element(int position) {
        return position >= 1 && position <= elements.size() ? elements.get(position - 1) : "";
    }

    /** El segmento como texto, con el separador de elementos dado y sin terminador. */
    public String text(char elementSeparator) {
        StringBuilder sb = new StringBuilder(id);
        for (String e : elements) {
            sb.append(elementSeparator).append(e);
        }
        return sb.toString();
    }
}
