package edi.as2adapter.as2;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

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

    // Un record compara los arreglos por referencia: el cuerpo se compara por contenido.
    @Override
    public boolean equals(Object o) {
        return o instanceof OutboundMessage(var id, var headers, var b, var m, var alg)
                && Objects.equals(messageId, id) && Objects.equals(httpHeaders, headers) && Arrays.equals(body, b)
                && Objects.equals(mic, m) && Objects.equals(micAlgorithm, alg);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(messageId, httpHeaders, mic, micAlgorithm) + Arrays.hashCode(body);
    }

    /** Sin el cuerpo cifrado. */
    @Override
    public String toString() {
        return "OutboundMessage[messageId=" + messageId + ", mic=" + mic + " (" + micAlgorithm + "), "
                + (body == null ? 0 : body.length) + " bytes]";
    }
}
