package edi.alerts.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmtpConfigControllerTest {

    @Test
    void elUsuarioDeLaBitacoraVieneDeLaCabeceraDelGateway() {
        assertThat(SmtpConfigController.actor("  ricardo ")).isEqualTo("ricardo");
        assertThat(SmtpConfigController.actor(null)).isEqualTo("system");
        assertThat(SmtpConfigController.actor("   ")).isEqualTo("system");
        assertThat(SmtpConfigController.actor("u".repeat(80))).hasSize(64);
    }
}
