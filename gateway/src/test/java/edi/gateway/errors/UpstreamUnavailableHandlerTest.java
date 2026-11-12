package edi.gateway.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class UpstreamUnavailableHandlerTest {

    private final UpstreamUnavailableHandler handler = new UpstreamUnavailableHandler();

    private static MockServerWebExchange exchange() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/as2/test-send"));
        ex.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id("as2-adapter").uri(URI.create("http://as2-adapter:8085")).predicate(e -> true).build());
        return ex;
    }

    @Test
    void servicioDetenido_responde503ConSuNombre() {
        var ex = exchange();
        // Contenedor detenido: su nombre ya no resuelve en la red de Docker (llega envuelto).
        var error = new IllegalStateException("fallo de conexion", new UnknownHostException("as2-adapter"));

        StepVerifier.create(handler.handle(ex, error)).verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getResponse().getBodyAsString().block())
                .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
                .contains("El servicio as2-adapter no responde");
    }

    @Test
    void conexionRechazada_tambienEs503() {
        var ex = exchange();
        StepVerifier.create(handler.handle(ex, new ConnectException("Connection refused"))).verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void otroError_siguePorElManejadorPorDefecto() {
        var ex = exchange();
        var error = new IllegalArgumentException("otra cosa");

        StepVerifier.create(handler.handle(ex, error)).expectErrorMatches(e -> e == error).verify();

        assertThat(ex.getResponse().getStatusCode()).isNull();
    }
}
