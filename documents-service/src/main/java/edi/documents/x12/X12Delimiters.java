package edi.documents.x12;

/**
 * Separadores de un intercambio X12. Se leen del ISA, que tiene ancho fijo: el cuarto caracter es el
 * separador de elementos, ISA16 (posicion 105) el de componentes y el caracter 106 el fin de segmento.
 *
 * @param element   separador de elementos, normalmente {@code *}
 * @param component separador de componentes (ISA16), normalmente {@code >} o {@code :}
 * @param segment   fin de segmento tal como aparece: {@code ~}, o un salto de linea
 */
public record X12Delimiters(char element, char component, String segment) {

    /** Los que usa el generador: {@code *}, {@code >} y {@code ~}. */
    public static final X12Delimiters STANDARD = new X12Delimiters('*', '>', "~");

    /** Para un texto sin ISA: se asumen los habituales. */
    public static final X12Delimiters UNKNOWN = STANDARD;
}
