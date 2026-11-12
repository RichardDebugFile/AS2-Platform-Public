package edi.gateway.errors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Con el as2-adapter caido (aqui un puerto cerrado), la consola recibe 503 con el nombre del
 * servicio en vez de un 500 generico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AS2_ADAPTER_URL=http://127.0.0.1:1")
@AutoConfigureWebTestClient
class UpstreamUnavailableIntegrationTest {

    @Autowired
    WebTestClient client;

    @MockitoBean
    ReactiveJwtDecoder decoder;

    @Test
    void servicioCaido_503ConSuNombre() {
        Instant now = Instant.now();
        when(decoder.decode(anyString())).thenReturn(Mono.just(Jwt.withTokenValue("jwt.admin").header("alg", "RS256")
                .subject("admin").claim("roles", List.of("ADMIN")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .build()));

        client.post().uri("/api/as2/test-send?fileName=x.edi").header("Authorization", "Bearer jwt.admin")
                .bodyValue("ISA*00~").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isEqualTo("El servicio as2-adapter no responde");
    }
}
