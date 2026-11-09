package edi.as2adapter.as2;

import java.util.Map;

/**
 * Mensaje AS2 listo para enviar: cabeceras HTTP, cuerpo cifrado y el MIC esperado en el MDN.
 *
 * @param messageId    Message-ID con sus angulos, con el que el socio contesta el MDN
 * @param httpHeaders  cabeceras AS2 1.2 de la peticion
 * @param body         enveloped-data (DER) que viaja en el cuerpo
 * @param mic          MIC de la parte firmada, en base64
 * @param micAlgorithm algoritmo del MIC con su nombre MIME ({@code sha-256})
 */
public record OutboundMessage(String messageId, Map<String, String> httpHeaders, byte[] body, String mic,
                              String micAlgorithm) {
}
