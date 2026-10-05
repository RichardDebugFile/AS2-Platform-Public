package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edi.auth.domain.RefreshToken;
import edi.auth.repo.RefreshTokenRepository;
import edi.auth.svc.RefreshTokenService.Rejection;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repo;
    private RefreshTokenService service;
    private final Instant t0 = Instant.parse("2026-10-12T08:00:00Z");

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new RefreshTokenService(repo, new SessionPolicy(5, 15, 60));
    }

    private RefreshToken stored(Instant startedAt, Instant expiresAt, boolean revoked) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(UUID.randomUUID());
        rt.setSessionStartedAt(startedAt);
        rt.setExpiresAt(expiresAt);
        rt.setRevoked(revoked);
        when(repo.findByTokenHash(RefreshTokenService.hash("tok"))).thenReturn(Optional.of(rt));
        return rt;
    }

    @Test
    void emitir_guardaElHashYNuncaElToken() {
        SessionPolicy.Window w = new SessionPolicy.Window(t0, t0.plusSeconds(1200));

        service.issue(UUID.randomUUID(), "tok", w);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .hasSize(64)
                .isEqualTo(RefreshTokenService.hash("tok"))
                .isNotEqualTo("tok");
        assertThat(saved.getValue().getSessionStartedAt()).isEqualTo(t0);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(t0.plusSeconds(1200));
    }

    @Test
    void validar_tokenVigente() {
        RefreshToken rt = stored(t0, t0.plusSeconds(1200), false);

        RefreshTokenService.Validation v = service.validate("tok", t0.plusSeconds(60));

        assertThat(v.valid()).isTrue();
        assertThat(v.token()).isSameAs(rt);
    }

    @Test
    void validar_tokenDesconocido() {
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.validate("otro", t0).rejection()).isEqualTo(Rejection.NOT_FOUND);
    }

    @Test
    void validar_tokenRevocado() {
        stored(t0, t0.plusSeconds(1200), true);

        assertThat(service.validate("tok", t0.plusSeconds(60)).rejection()).isEqualTo(Rejection.REVOKED);
    }

    @Test
    void validar_inactividad() {
        stored(t0, t0.plus(Duration.ofMinutes(20)), false);

        assertThat(service.validate("tok", t0.plus(Duration.ofMinutes(21))).rejection())
                .isEqualTo(Rejection.IDLE_EXPIRED);
    }

    @Test
    void validar_techoAbsolutoAunqueLaFilaSigaVigente() {
        stored(t0, t0.plus(Duration.ofDays(1)), false);

        assertThat(service.validate("tok", t0.plus(Duration.ofMinutes(60))).rejection())
                .isEqualTo(Rejection.SESSION_EXPIRED);
    }

    @Test
    void revocar_marcaLaFila() {
        RefreshToken rt = stored(t0, t0.plusSeconds(1200), false);

        service.revoke("tok");

        assertThat(rt.isRevoked()).isTrue();
    }

    @Test
    void revocarTodo_delegaEnElRepositorio() {
        UUID user = UUID.randomUUID();
        when(repo.revokeAllByUserId(user)).thenReturn(3);

        assertThat(service.revokeAllForUser(user)).isEqualTo(3);
    }

    @Test
    void purga_conservaUnDiaTrasCaducar() {
        when(repo.deleteExpiredBefore(t0.minus(Duration.ofDays(1)))).thenReturn(7);

        assertThat(service.purgeStale(t0)).isEqualTo(7);
    }

    @Test
    void purgaProgramada_noFallaSinFilas() {
        service.purgeStale();

        verify(repo).deleteExpiredBefore(any());
    }

    @Test
    void hash_esDeterministaYDistinguible() {
        assertThat(RefreshTokenService.hash("a")).isEqualTo(RefreshTokenService.hash("a"));
        assertThat(RefreshTokenService.hash("a")).isNotEqualTo(RefreshTokenService.hash("b"));
    }
}
