package edi.gateway.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import com.sun.net.httpserver.HttpServer;
import edi.gateway.status.SystemStatusController.ServiceStatus;
import edi.gateway.status.SystemStatusController.SystemStatus;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

class SystemStatusControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private HttpServer up;
    private HttpServer down;

    @AfterEach
    void stop() {
        if (up != null) up.stop(0);
        if (down != null) down.stop(0);
    }

    private static HttpServer health(int code, String body) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        s.createContext("/actuator/health", ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        s.start();
        return s;
    }

    private static RouteDefinition route(String id, String uri) {
        RouteDefinition d = new RouteDefinition();
        d.setId(id);
        d.setUri(URI.create(uri));
        return d;
    }

    private static String url(HttpServer s) {
        return "http://127.0.0.1:" + s.getAddress().getPort();
    }

    @Test
    void consultaCadaServicioUnaVezYMarcaLosCaidosOSinRespuesta() throws Exception {
        up = health(200, "{\"status\":\"UP\",\"components\":{\"ping\":{\"status\":\"UP\"},"
                + "\"db\":{\"status\":\"UP\",\"details\":{\"database\":\"PostgreSQL\"}}}}");
        down = health(503, "{\"status\":\"DOWN\",\"components\":{\"db\":{\"status\":\"DOWN\"}}}");
        RouteDefinitionLocator routes = () -> Flux.just(
                route("partners", url(up)),
                route("documents", url(down)),
                // El callback MDN va al mismo servicio que documents: no se repite.
                route("mdn-callback", url(down)),
                route("as2-adapter", "http://127.0.0.1:1"),
                route("alerts", "http://127.0.0.1:2"));

        SystemStatus s = new SystemStatusController(routes, List.of(" alerts "), Duration.ofSeconds(2), CLOCK)
                .status().block();

        assertThat(s.checkedAt()).isEqualTo(CLOCK.instant());
        assertThat(s.services()).extracting(ServiceStatus::service, ServiceStatus::status).containsExactly(
                tuple("gateway", "UP"),
                tuple("partners-service", "UP"),
                tuple("documents-service", "DOWN"),
                tuple("as2-adapter", SystemStatusController.UNREACHABLE));
        assertThat(s.services().get(1).latencyMs()).isNotNull();
        assertThat(s.services().get(3).latencyMs()).isNull();
        // Verificaciones internas por nombre, sin sus detalles.
        assertThat(s.services().get(1).components()).containsExactly(entry("db", "UP"), entry("ping", "UP"));
        assertThat(s.services().get(2).components()).containsExactly(entry("db", "DOWN"));
        assertThat(s.services().get(0).components()).isEmpty();
        assertThat(s.services().get(3).components()).isEmpty();
    }

    @Test
    void unaRespuestaSinEstadoQuedaComoDesconocida() throws Exception {
        up = health(200, "{}");
        RouteDefinitionLocator routes = () -> Flux.just(route("auth", url(up)), route("x", "lb://otro"));

        SystemStatus s = new SystemStatusController(routes, List.of(), Duration.ofSeconds(2), CLOCK).status().block();

        assertThat(s.services()).extracting(ServiceStatus::service, ServiceStatus::status).containsExactly(
                tuple("gateway", "UP"),
                tuple("auth-service", "UNKNOWN"));
        assertThat(SystemStatusController.serviceName("as2-adapter")).isEqualTo("as2-adapter");
    }
}
