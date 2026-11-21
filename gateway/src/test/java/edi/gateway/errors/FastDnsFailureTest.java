package edi.gateway.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

/**
 * AS2-102: con un servidor DNS que no contesta (como cuando un contenedor detenido deja de resolverse), el
 * nombre falla en el tiempo acotado, como UnknownHostException, que el gateway convierte en 503.
 */
class FastDnsFailureTest {

    @Test
    void unNombreQueNoSeResuelveFallaEnPocosSegundos() throws Exception {
        try (DatagramSocket silentDns = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            InetSocketAddress dns = new InetSocketAddress(InetAddress.getLoopbackAddress(), silentDns.getLocalPort());
            HttpClient client = new FastDnsFailure(Duration.ofMillis(500), new SingletonDnsServerAddressStreamProvider(dns))
                    .customize(HttpClient.create());

            long start = System.nanoTime();
            Throwable error = catchThrowable(() -> client.get().uri("http://servicio-detenido:8085/actuator/health")
                    .response().block(Duration.ofSeconds(20)));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(error).isNotNull();
            assertThat(chainHas(error, UnknownHostException.class)).as("causas: %s", error).isTrue();
            // Espero al DNS (no fallo por otra causa), pero solo el limite: dos consultas de 0,5 s como
            // maximo, con margen para el arranque del cliente.
            assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(450)).isLessThan(Duration.ofSeconds(5));
        }
    }

    private static boolean chainHas(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
