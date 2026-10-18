package edi.gateway.alerts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlertsClientPropertiesTest {

    @Test
    void desactivadoPorDefecto() {
        AlertsClientProperties p = new AlertsClientProperties();
        p.setBaseUrl("http://localhost:8082");
        p.setToken("t");
        assertThat(p.isConfigured()).isFalse();
    }

    @Test
    void configuradoSoloConDestinoYToken() {
        AlertsClientProperties p = new AlertsClientProperties();
        p.setEnabled(true);
        assertThat(p.isConfigured()).isFalse();
        p.setBaseUrl("http://localhost:8082");
        assertThat(p.isConfigured()).isFalse();
        p.setToken(" ");
        assertThat(p.isConfigured()).isFalse();
        p.setToken("t");
        assertThat(p.isConfigured()).isTrue();
    }

    @Test
    void listaDeExclusionesNuncaNula() {
        AlertsClientProperties p = new AlertsClientProperties();
        assertThat(p.getExcludePaths()).contains("/actuator/**");
        p.setExcludePaths(null);
        assertThat(p.getExcludePaths()).isEmpty();
        p.setExcludePaths(List.of("/x/**"));
        assertThat(p.getExcludePaths()).containsExactly("/x/**");
    }
}
