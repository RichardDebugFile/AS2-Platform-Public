package edi.as2adapter.as2;

import java.time.Duration;

/**
 * Resultado de un envio AS2 con MDN sincrono.
 *
 * @param messageId  Message-ID enviado
 * @param httpStatus codigo HTTP de la respuesta del socio
 * @param mic        MIC calculado al enviar (base64)
 * @param mdn        MDN leido de la respuesta
 * @param elapsed    tiempo desde el envio hasta tener el MDN
 */
public record SendResult(String messageId, int httpStatus, String mic, Mdn mdn, Duration elapsed,
                         String micAlgorithm, String rawMdn, boolean requestMdn, boolean requireSignature,
                         boolean requireMic) {

    /** El socio acepto, firmo el MDN con su clave y recibio exactamente lo que se envio. */
    public boolean delivered() {
        return rejection() == null;
    }

    public boolean micMatches() {
        try {
            return mdn != null && mdn.micMatches(mic, micAlgorithm);
        } catch (As2Exception _) {
            return false;
        }
    }

    public String rejection() {
        if (!requestMdn) return null;
        if (mdn == null) return "El socio no devolvió el MDN solicitado";
        if (!messageId.equals(mdn.originalMessageId())) return "El MDN corresponde a otro Message-ID";
        if ((requireSignature && !mdn.signed()) || (mdn.signed() && !mdn.signatureValid())) {
            return "La firma del MDN no corresponde a un certificado de firma del socio";
        }
        if (!mdn.processed()) return "El socio rechazó el documento: " + mdn.disposition();
        return requireMic && !micMatches() ? "El MIC devuelto por el socio no coincide" : null;
    }
}
