package edi.auth.svc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MfaChallengeStoreTest {

    @Test
    void desafioVigente_devuelveElUsuario() {
        MfaChallengeStore store = new MfaChallengeStore(300, 5);

        String token = store.create("alice");

        assertThat(store.getUsername(token)).isEqualTo("alice");
    }

    @Test
    void consumido_noSePuedeReutilizar() {
        MfaChallengeStore store = new MfaChallengeStore(300, 5);
        String token = store.create("alice");

        store.consume(token);

        assertThat(store.getUsername(token)).isNull();
    }

    @Test
    void caducado_noSirveYSePurgaAlCrearOtro() {
        MfaChallengeStore store = new MfaChallengeStore(0, 5);
        String token = store.create("alice");

        assertThat(store.getUsername(token)).isNull();
        store.create("bob");
        // El de alice se purgo; solo queda el recien creado
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void desconocido_devuelveNull() {
        MfaChallengeStore store = new MfaChallengeStore(300, 5);

        assertThat(store.getUsername("no-existe")).isNull();
        assertThat(store.registerFailure("no-existe")).isFalse();
    }

    @Test
    void agotarIntentos_descartaElDesafio() {
        MfaChallengeStore store = new MfaChallengeStore(300, 3);
        String token = store.create("alice");

        assertThat(store.registerFailure(token)).isTrue();
        assertThat(store.registerFailure(token)).isTrue();
        assertThat(store.registerFailure(token)).isFalse();
        assertThat(store.getUsername(token)).isNull();
    }
}
