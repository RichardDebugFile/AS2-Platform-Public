package edi.partners.svc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edi.partners.domain.MdnMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartnerRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"SOCIO-A", "Socio Prueba 01", "socio_b.as2", "A"})
    void identificadorAs2_valido(String nombre) {
        assertThat(PartnerRules.as2Name("  " + nombre + " ")).isEqualTo(nombre);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COM1", "com1", "CON", "nul", "LPT9", "AUX.txt"})
    void nombresReservadosDelSistema_rechazados(String nombre) {
        assertThatThrownBy(() -> PartnerRules.as2Name(nombre))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservado");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Socio/Prueba", "Socio\\Prueba", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b"})
    void caracteresNoValidosEnCarpetas_rechazados(String nombre) {
        assertThatThrownBy(() -> PartnerRules.as2Name(nombre))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carpetas");
    }

    @Test
    void otrasReglasDelIdentificador() {
        assertThatThrownBy(() -> PartnerRules.as2Name(" ")).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> PartnerRules.as2Name(null)).hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> PartnerRules.as2Name("SOCIO.")).hasMessageContaining("punto");
        assertThatThrownBy(() -> PartnerRules.as2Name("Señor")).hasMessageContaining("ASCII");
        assertThatThrownBy(() -> PartnerRules.as2Name("x".repeat(129))).hasMessageContaining("128");
    }

    @Test
    void direccionDelServidor_soloHost() {
        assertThat(PartnerRules.serverAddress(" as2.socio.example ")).isEqualTo("as2.socio.example");
        assertThat(PartnerRules.serverAddress("192.0.2.10")).isEqualTo("192.0.2.10");
        assertThat(PartnerRules.serverAddress("[2001:db8::1]")).isEqualTo("[2001:db8::1]");
        assertThatThrownBy(() -> PartnerRules.serverAddress("https://as2.socio.example"))
                .hasMessageContaining("protocolo");
        assertThatThrownBy(() -> PartnerRules.serverAddress("as2.socio.example/as2"))
                .hasMessageContaining("no valida");
        assertThatThrownBy(() -> PartnerRules.serverAddress("")).hasMessageContaining("obligatoria");
    }

    @Test
    void ruta_seNormalizaConBarraInicial() {
        assertThat(PartnerRules.path("as2/receive")).isEqualTo("/as2/receive");
        assertThat(PartnerRules.path("/as2")).isEqualTo("/as2");
        assertThat(PartnerRules.path("  ")).isNull();
        assertThatThrownBy(() -> PartnerRules.path("/a/../b")).hasMessageContaining("no valida");
        assertThatThrownBy(() -> PartnerRules.path("/as2?x=1")).hasMessageContaining("no valida");
    }

    @Test
    void urlDeMdn_soloConModoAsincronoYHttps() {
        assertThat(PartnerRules.mdnUrl(MdnMode.SYNC, "https://x.example/mdn", "URL")).isNull();
        assertThat(PartnerRules.mdnUrl(MdnMode.ASYNC, " https://x.example/mdn ", "URL"))
                .isEqualTo("https://x.example/mdn");
        assertThatThrownBy(() -> PartnerRules.mdnUrl(MdnMode.ASYNC, null, "URL")).hasMessageContaining("obligatoria");
        assertThatThrownBy(() -> PartnerRules.mdnUrl(MdnMode.ASYNC, "http://x.example/mdn", "URL"))
                .hasMessageContaining("https");
        assertThatThrownBy(() -> PartnerRules.mdnUrl(MdnMode.ASYNC, "https://exa mple", "URL"))
                .hasMessageContaining("no es una URL");
    }

    @Test
    void textoOpcional() {
        assertThat(PartnerRules.trim("  a ")).isEqualTo("a");
        assertThat(PartnerRules.trim("   ")).isNull();
        assertThat(PartnerRules.trim(null)).isNull();
    }
}
