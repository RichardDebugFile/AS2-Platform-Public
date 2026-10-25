package edi.gateway.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/** Tabla de rutas de HU-05 (T1): cada prefijo va a su servicio y el callback MDN se reescribe. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {

    @Autowired
    RouteDefinitionLocator locator;

    private Map<String, RouteDefinition> rutas() {
        return locator.getRouteDefinitions().collectList().block().stream()
                .collect(Collectors.toMap(RouteDefinition::getId, r -> r));
    }

    private static String paths(RouteDefinition r) {
        return r.getPredicates().stream()
                .filter(p -> p.getName().equals("Path"))
                .flatMap(p -> p.getArgs().values().stream())
                .collect(Collectors.joining(","));
    }

    @Test
    void cadaPrefijoVaASuServicio() {
        Map<String, RouteDefinition> r = rutas();
        assertThat(r).containsKeys("partners", "alerts", "auth", "documents", "as2-adapter", "mdn-callback");
        assertThat(r.get("partners").getUri()).hasPort(8081);
        assertThat(paths(r.get("partners"))).contains("/api/partners/**", "/api/audit-logs/**", "/api/catalogs/**");
        assertThat(r.get("alerts").getUri()).hasPort(8082);
        assertThat(paths(r.get("alerts"))).contains("/api/alerts/**", "/api/smtp-config/**");
        assertThat(r.get("auth").getUri()).hasPort(8083);
        assertThat(paths(r.get("auth"))).contains("/auth/**", "/users/**", "/.well-known/**");
        assertThat(r.get("documents").getUri()).hasPort(8084);
        assertThat(r.get("as2-adapter").getUri()).hasPort(8085);
        assertThat(paths(r.get("as2-adapter"))).contains("/api/as2/**", "/api/mdn/**", "/as2/receive");
    }

    @Test
    void elCallbackMdnSeReescribeHaciaDocuments() {
        RouteDefinition mdn = rutas().get("mdn-callback");
        assertThat(mdn.getUri()).hasPort(8084);
        assertThat(mdn.getFilters()).anySatisfy(f -> {
            assertThat(f.getName()).isEqualTo("SetPath");
            assertThat(f.getArgs().values()).contains("/internal/as2/mdn");
        });
    }

    /**
     * Las consultas y registros internos de los servicios (motor AS2, bitacora) no se publican: ningun
     * patron de ruta los alcanza. Solo el callback MDN de los socios entra por /internal.
     */
    @Test
    void lasRutasInternasDeLosServicios_noSePublican() {
        var parser = new org.springframework.web.util.pattern.PathPatternParser();
        var patrones = rutas().values().stream()
                .flatMap(r -> java.util.Arrays.stream(paths(r).split(",")))
                .map(parser::parse)
                .toList();
        for (String interna : java.util.List.of("/internal/audit-logs", "/internal/partners/search",
                "/internal/partners/filewatcher-enabled")) {
            var ruta = org.springframework.http.server.PathContainer.parsePath(interna);
            assertThat(patrones).as(interna).noneMatch(p -> p.matches(ruta));
        }
        var bitacora = org.springframework.http.server.PathContainer.parsePath("/api/audit-logs/export");
        assertThat(patrones).anyMatch(p -> p.matches(bitacora));
    }
}
