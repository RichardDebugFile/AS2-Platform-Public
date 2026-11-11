package edi.gateway.status;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Estado del sistema para el Inicio de la consola (AS2-99, adelanto de HU-20): la salud de cada servicio
 * al que el gateway enruta, consultada en su {@code /actuator/health}. Los servicios se toman de la
 * tabla de rutas, asi que la lista no se duplica; los excluidos por configuracion no se consultan (p.
 * ej. los que un despliegue no incluye).
 */
@RestController
public class SystemStatusController {

    /** Salud de un servicio: UP, DOWN, OUT_OF_SERVICE o UNREACHABLE si no respondio. */
    public record ServiceStatus(String service, String status, Long latencyMs) {
    }

    public record SystemStatus(Instant checkedAt, List<ServiceStatus> services) {
    }

    static final String UNREACHABLE = "UNREACHABLE";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private final RouteDefinitionLocator routes;
    private final Set<String> excluded;
    private final WebClient web;
    private final Duration timeout;
    private final Clock clock;

    @Autowired
    public SystemStatusController(RouteDefinitionLocator routes,
            @Value("${gateway.system-status.exclude:}") List<String> excluded,
            @Value("${gateway.system-status.timeout-ms:2000}") long timeoutMs) {
        this(routes, excluded, Duration.ofMillis(timeoutMs), Clock.systemUTC());
    }

    SystemStatusController(RouteDefinitionLocator routes, List<String> excluded, Duration timeout, Clock clock) {
        this.routes = routes;
        this.excluded = excluded.stream().map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.timeout = timeout;
        this.clock = clock;
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        this.web = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
    }

    @GetMapping("/api/system/status")
    public Mono<SystemStatus> status() {
        return routes.getRouteDefinitions().collectList().flatMap(defs -> {
            Flux<ServiceStatus> checks = Flux.fromIterable(services(defs).entrySet())
                    .flatMapSequential(e -> check(e.getKey(), e.getValue()));
            return checks.collectList().map(list -> {
                List<ServiceStatus> all = new ArrayList<>();
                // El gateway responde esta misma peticion: esta en marcha.
                all.add(new ServiceStatus("gateway", "UP", 0L));
                all.addAll(list);
                return new SystemStatus(clock.instant(), all);
            });
        });
    }

    /** Un servicio por URI de destino (varias rutas pueden ir al mismo), en el orden de la tabla. */
    Map<String, URI> services(List<RouteDefinition> defs) {
        Map<URI, String> byUri = new LinkedHashMap<>();
        for (RouteDefinition d : defs) {
            if (!excluded.contains(d.getId()) && d.getUri() != null && d.getUri().getScheme().startsWith("http")) {
                byUri.putIfAbsent(d.getUri(), d.getId());
            }
        }
        Map<String, URI> out = new LinkedHashMap<>();
        byUri.forEach((uri, id) -> out.put(serviceName(id), uri));
        return out;
    }

    /** {@code partners} → {@code partners-service}; {@code as2-adapter} se queda igual. */
    static String serviceName(String routeId) {
        return routeId.contains("-") ? routeId : routeId + "-service";
    }

    private Mono<ServiceStatus> check(String name, URI base) {
        long start = System.nanoTime();
        // Un servicio caido responde 503 con {"status":"DOWN"}: se lee el cuerpo con cualquier codigo.
        return web.get().uri(base.resolve("/actuator/health"))
                .exchangeToMono(res -> res.bodyToMono(JSON).defaultIfEmpty(Map.of()))
                .timeout(timeout)
                .map(body -> new ServiceStatus(name, String.valueOf(body.getOrDefault("status", "UNKNOWN")),
                        Duration.ofNanos(System.nanoTime() - start).toMillis()))
                .onErrorResume(e -> Mono.just(new ServiceStatus(name, UNREACHABLE, null)));
    }
}
