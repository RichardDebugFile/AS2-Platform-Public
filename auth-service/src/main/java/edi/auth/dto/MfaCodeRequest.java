package edi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Codigo TOTP de 6 digitos para activar o desactivar el MFA. */
public record MfaCodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}
