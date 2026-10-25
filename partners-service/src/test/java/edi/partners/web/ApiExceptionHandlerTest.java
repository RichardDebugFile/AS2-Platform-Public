package edi.partners.web;

import static org.assertj.core.api.Assertions.assertThat;

import edi.partners.dto.ApiError;
import edi.partners.error.ConfigNotFoundException;
import edi.partners.svc.folders.PartnerFolderException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void carpetasFallidas_500ConCodigoPropio() {
        ResponseEntity<ApiError> r = handler.onFolders(new PartnerFolderException("sin permisos. El socio no se guardo."));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().code()).isEqualTo("PARTNER_FOLDERS_FAILED");
        assertThat(r.getBody().message()).contains("no se guardo");
    }

    @Test
    void choqueConElIndiceUnico_409() {
        ResponseEntity<ApiError> r = handler.onIntegrity(
                new DataIntegrityViolationException("dup", new SQLException("uk_partner_as2_name_active")));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void configuracionInexistente_404() {
        ResponseEntity<ApiError> r = handler.onConfigNotFound(new ConfigNotFoundException("salida", UUID.randomUUID()));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().code()).isEqualTo("CONFIG_NOT_FOUND");
    }
}
