package edi.documents.ingest;

import edi.documents.store.Direction;
import java.util.Arrays;
import java.util.Objects;

/**
 * Documento que llega al repositorio: lo envia el motor AS2 al recibir o enviar, o un operador desde la
 * consola. El tipo, la version y los numeros de control se leen del propio documento (HU-12).
 *
 * @param messageId  Message-ID AS2; sin el, se genera uno (la idempotencia necesita un identificador)
 * @param attributes JSON libre con datos adicionales del intercambio
 */
public record IngestCommand(String partner, Direction direction, byte[] content, String messageId, String as2From,
        String as2To, String mic, String micAlgorithm, String signingAlgorithm, String encryptionAlgorithm,
        String attributes) {

    /** El contenido se compara por sus bytes, no por la referencia del arreglo. */
    @Override
    public boolean equals(Object o) {
        return o instanceof IngestCommand c && Arrays.equals(content, c.content)
                && Objects.equals(partner, c.partner) && direction == c.direction
                && Objects.equals(messageId, c.messageId) && Objects.equals(as2From, c.as2From)
                && Objects.equals(as2To, c.as2To) && Objects.equals(mic, c.mic)
                && Objects.equals(micAlgorithm, c.micAlgorithm) && Objects.equals(signingAlgorithm, c.signingAlgorithm)
                && Objects.equals(encryptionAlgorithm, c.encryptionAlgorithm)
                && Objects.equals(attributes, c.attributes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(partner, direction, messageId, as2From, as2To, mic, micAlgorithm, signingAlgorithm,
                encryptionAlgorithm, attributes) + Arrays.hashCode(content);
    }

    /** Sin el contenido: solo su tamano, para no volcar documentos en los logs. */
    @Override
    public String toString() {
        return "IngestCommand[partner=" + partner + ", direction=" + direction + ", messageId=" + messageId
                + ", bytes=" + (content == null ? 0 : content.length) + "]";
    }
}
