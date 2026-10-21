package edi.partners.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AlgorithmParsingTest {

    @ParameterizedTest
    @CsvSource({
        "SHA256, RSA_SHA256", "sha-256, RSA_SHA256", "RSA-SHA256, RSA_SHA256", "rsa_sha256, RSA_SHA256",
        "SHA1, RSA_SHA1", "RSA-SHA1, RSA_SHA1", "sha512, RSA_SHA512", "RSA_SHA384, RSA_SHA384"
    })
    void firma_aceptaLasFormasHabitualesDeLosSocios(String entrada, SigningAlgorithm esperado) {
        assertThat(SigningAlgorithm.parse(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({
        "3DES, TRIPLE_DES", "_3DES, TRIPLE_DES", "DESede, TRIPLE_DES",
        "aes-128, AES128", "AES_256, AES256", "AES192, AES192"
    })
    void cifrado_aceptaLasFormasHabitualesDeLosSocios(String entrada, EncryptionAlgorithm esperado) {
        assertThat(EncryptionAlgorithm.parse(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"sha-1, SHA1", "SHA256, SHA256", "sha_384, SHA384", "SHA-512, SHA512"})
    void mic_aceptaLasFormasHabitualesDeLosSocios(String entrada, MicAlgorithm esperado) {
        assertThat(MicAlgorithm.parse(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void sinValor_quedaSinAlgoritmo(String entrada) {
        assertThat(SigningAlgorithm.parse(entrada)).isNull();
        assertThat(EncryptionAlgorithm.parse(entrada)).isNull();
        assertThat(MicAlgorithm.parse(entrada)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MD5", "RC2", "DSA"})
    void algoritmoDesconocido_esRechazadoConMensajeClaro(String entrada) {
        assertThatThrownBy(() -> EncryptionAlgorithm.parse(entrada))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(entrada);
    }
}
