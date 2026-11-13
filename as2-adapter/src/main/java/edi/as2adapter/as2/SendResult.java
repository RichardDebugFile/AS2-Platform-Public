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
public record SendResult(String messageId, int httpStatus, String mic, Mdn mdn, Duration elapsed) {

    /** El socio acepto, firmo el MDN con su clave y recibio exactamente lo que se envio. */
    public boolean delivered() {
        return mdn.processed() && mdn.signed() && mdn.signatureValid() && micMatches()
                && messageId.equals(mdn.originalMessageId());
    }

    public boolean micMatches() {
        return mdn.micMatches(mic, MessageBuilder.MIC_ALGORITHM);
    }
}
