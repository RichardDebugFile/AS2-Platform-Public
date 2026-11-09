package edi.as2adapter.as2;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servidor AS2 de prueba sobre el HttpServer del JDK: recibe con {@link FakePartner} y responde un MDN
 * firmado. {@code reply} elige la respuesta: processed, error, bad-mic o http-500.
 */
public final class SimulatedPartner {

    private SimulatedPartner() {
    }

    public static HttpServer start(As2Identity identity, X509Certificate sender, AtomicReference<String> reply,
                                   AtomicReference<String> receivedPayload) throws IOException {
        FakePartner partner = new FakePartner(identity);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/as2", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            if (reply.get().equals("http-500")) {
                ex.sendResponseHeaders(500, -1);
                ex.close();
                return;
            }
            FakePartner.Received r = partner.receive(body, sender);
            receivedPayload.set(new String(r.payload(), StandardCharsets.US_ASCII));
            String id = ex.getRequestHeaders().getFirst("Message-ID");
            String[] mdn = switch (reply.get()) {
                case "error" -> partner.mdn(id, "automatic-action/MDN-sent-automatically; processed/error: unexpected-processing-error", null);
                case "bad-mic" -> partner.mdn(id, "automatic-action/MDN-sent-automatically; processed", "T1RSTw==");
                default -> partner.mdn(id, "automatic-action/MDN-sent-automatically; processed", r.mic());
            };
            byte[] out = mdn[1].getBytes(StandardCharsets.ISO_8859_1);
            ex.getResponseHeaders().add("Content-Type", mdn[0]);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        return server;
    }
}
