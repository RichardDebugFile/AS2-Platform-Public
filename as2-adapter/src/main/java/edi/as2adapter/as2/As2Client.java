package edi.as2adapter.as2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;

/**
 * Envia un mensaje AS2 por HTTP(S) y lee el MDN sincrono que el socio devuelve en la misma respuesta.
 */
public final class As2Client {

    private final HttpClient http;
    private final Duration timeout;

    public As2Client(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    public SendResult send(URI url, OutboundMessage message, X509Certificate partner) {
        return send(url, message, List.of(partner), true, true, true);
    }

    public SendResult send(URI url, OutboundMessage message, List<X509Certificate> certificates,
                           boolean requestMdn, boolean requireSignature, boolean requireMic) {
        HttpRequest.Builder req = HttpRequest.newBuilder(url).timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(message.body()));
        message.httpHeaders().forEach(req::header);
        long start = System.nanoTime();
        HttpResponse<byte[]> res;
        try {
            res = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (SSLHandshakeException e) {
            throw new As2Exception("No se pudo verificar el servidor HTTPS. Cargue el certificado SSL del socio y revise su nombre de servidor.", e);
        } catch (IOException e) {
            throw new As2Exception("No se pudo conectar con " + url.getHost() + ": " + reason(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new As2Exception("Envio interrumpido", e);
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        if (res.statusCode() / 100 != 2) {
            throw new As2Exception("El socio respondio HTTP " + res.statusCode() + " sin MDN");
        }
        String contentType = res.headers().firstValue("Content-Type").orElse("");
        Mdn mdn = requestMdn && res.body().length != 0 ? readMdn(contentType, res.body(), certificates) : null;
        String raw = requestMdn ? "Content-Type: " + contentType + "\r\n\r\n"
                + new String(res.body(), StandardCharsets.ISO_8859_1) : null;
        return new SendResult(message.messageId(), res.statusCode(), message.mic(), mdn, elapsed, message.micAlgorithm(),
                raw, requestMdn, requireSignature, requireMic);
    }

    private static Mdn readMdn(String contentType, byte[] body, List<X509Certificate> certificates) {
        try {
            return MdnReader.read(contentType, body, certificates);
        } catch (IllegalArgumentException e) {
            throw new As2Exception("El socio devolvió un MDN con formato inválido", e);
        }
    }

    /**
     * Motivo legible de un fallo de conexion (AS2-104). El HttpClient del JDK suele lanzar un
     * ConnectException sin mensaje: se usa el de su causa o, si no hay ninguno, el tipo de error.
     */
    static String reason(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                return t.getMessage().strip();
            }
        }
        return switch (e) {
            case java.net.http.HttpConnectTimeoutException _ -> "tiempo de conexion agotado";
            case java.net.ConnectException _ -> "conexion rechazada";
            case java.net.UnknownHostException _ -> "servidor desconocido";
            default -> e.getClass().getSimpleName();
        };
    }
}
