package edi.documents.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Los nombres que vienen de fuera no pueden sacar un archivo del repositorio ni de la carpeta del socio. */
class SafeNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "../otro", "a/b", "a\\b", "", "   ", "socio\u0000"})
    void carpetaDeSocioQueEscapa_seRechaza(String name) {
        assertThatThrownBy(() -> SafeNames.folder(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carpetaDeSocioValida_seConserva() {
        assertThat(SafeNames.folder(" SOCIO-A ")).isEqualTo("SOCIO-A");
        assertThat(SafeNames.folder("Socio Ejemplo.1")).isEqualTo("Socio Ejemplo.1");
    }

    @Test
    void parteDeNombreDeArchivo_soloCaracteresSeguros() {
        assertThat(SafeNames.part("../../etc")).isEqualTo("______etc");
        assertThat(SafeNames.part("000000123")).isEqualTo("000000123");
        assertThat(SafeNames.part(null)).isEqualTo("NA");
        assertThat(SafeNames.part("x".repeat(60))).hasSize(40);
    }
}
