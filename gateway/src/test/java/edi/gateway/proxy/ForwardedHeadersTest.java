package edi.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Escenario "Cookies Secure detras del proxy" (HU-05/HU-07): nginx termina TLS y avisa con
 * X-Forwarded-Proto; auth-service decide el atributo Secure de las cookies con esa cabecera, asi que
 * el gateway tiene que hacerla llegar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ForwardedHeadersTest {

    static final Map<String, String> recibidas = new ConcurrentHashMap<>();
    static DisposableServer authFalso;

    @BeforeAll
    static void arrancarAuthFalso() {
        authFalso = HttpServer.create()
                .port(0)
                .handle((req, res) -> {
                    recibidas.clear();
                    req.requestHeaders().forEach(h -> recibidas.put(h.getKey().toLowerCase(), h.getValue()));
                    return res.status(200).sendString(Mono.just("{}"));
                })
                .bindNow();
    }

    @AfterAll
    static void pararAuthFalso() {
        authFalso.disposeNow();
    }

    @DynamicPropertySource
    static void rutas(DynamicPropertyRegistry r) {
        r.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + authFalso.port());
    }

    @Autowired
    WebTestClient client;

    @Test
    void elEsquemaHttpsDelProxyLlegaAAuthService() {
        client.post()
                .uri("/auth/login")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Forwarded-Host", "as2.example.com")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(recibidas).containsEntry("x-forwarded-proto", "https");
    }
}
