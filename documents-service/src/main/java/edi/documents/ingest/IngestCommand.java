package edi.documents.ingest;

import edi.documents.store.Direction;

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
}
