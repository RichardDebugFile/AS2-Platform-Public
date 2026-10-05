package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionPolicyTest {

    private final SessionPolicy policy = new SessionPolicy(5, 15, 60);
    private final Instant t0 = Instant.parse("2026-10-12T08:00:00Z");

    @Test
    void sesionNueva_elRefreshViveInactividadMasAccess() {
        SessionPolicy.Window w = policy.newSession(t0);

        assertThat(w.sessionStartedAt()).isEqualTo(t0);
        assertThat(w.expiresAt()).isEqualTo(t0.plus(Duration.ofMinutes(20)));
    }

    @Test
    void rotar_heredaElInicioYNoMueveElTecho() {
        Instant renovacion = t0.plus(Duration.ofMinutes(30));

        SessionPolicy.Window w = policy.rotated(t0, renovacion);

        assertThat(w.sessionStartedAt()).isEqualTo(t0);
        assertThat(w.expiresAt()).isEqualTo(renovacion.plus(Duration.ofMinutes(20)));
        assertThat(policy.ceiling(w.sessionStartedAt())).isEqualTo(t0.plus(Duration.ofMinutes(60)));
    }

    @Test
    void rotarCercaDelTecho_laVentanaSeRecortaAlTecho() {
        Instant renovacion = t0.plus(Duration.ofMinutes(50));

        SessionPolicy.Window w = policy.rotated(t0, renovacion);

        assertThat(w.expiresAt()).isEqualTo(t0.plus(Duration.ofMinutes(60)));
    }

    @Test
    void exponeLosTiemposEnSegundos() {
        assertThat(policy.accessTtlSeconds()).isEqualTo(300);
        assertThat(policy.idleTimeoutSeconds()).isEqualTo(900);
        assertThat(policy.sessionMaxSeconds()).isEqualTo(3600);
    }

    @ParameterizedTest
    @CsvSource({"0,15,60", "5,0,60", "5,15,0", "-1,15,60"})
    void parametrosNoPositivos_impidenArrancar(long access, long idle, long max) {
        assertThatThrownBy(() -> new SessionPolicy(access, idle, max))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
