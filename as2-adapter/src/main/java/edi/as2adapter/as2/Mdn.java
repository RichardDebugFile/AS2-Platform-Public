package edi.as2adapter.as2;

import java.util.Locale;

/**
 * Acuse de recibo AS2 (MDN, RFC 3798 y RFC 4130 7.4) tal como lo devolvio el socio.
 *
 * @param originalMessageId  Message-ID del mensaje al que responde
 * @param disposition        p. ej. {@code automatic-action/MDN-sent-automatically; processed}
 * @param receivedContentMic MIC que calculo el socio, en base64 (sin el algoritmo)
 * @param micAlgorithm       algoritmo con el que lo calculo ({@code sha-256})
 * @param signed             si llego firmado (multipart/signed)
 * @param signatureValid     si la firma corresponde al certificado del socio
 * @param text               explicacion legible que acompana al MDN
 */
public record Mdn(String originalMessageId, String disposition, String receivedContentMic, String micAlgorithm,
                  boolean signed, boolean signatureValid, String text) {

    /** {@code processed} sin modificador {@code error} ni {@code failure}: el socio acepto el documento. */
    public boolean processed() {
        if (disposition == null) return false;
        int semi = disposition.indexOf(';');
        String result = (semi < 0 ? disposition : disposition.substring(semi + 1)).toLowerCase(Locale.ROOT);
        return result.contains("processed") && !result.contains("error") && !result.contains("failure");
    }

    /** True si el socio calculo el mismo MIC y con el mismo algoritmo que se envio. */
    public boolean micMatches(String expectedMic, String expectedAlgorithm) {
        return receivedContentMic != null && micAlgorithm != null
                && receivedContentMic.strip().equals(expectedMic)
                && Cms.jcaDigest(micAlgorithm).equals(Cms.jcaDigest(expectedAlgorithm));
    }
}
