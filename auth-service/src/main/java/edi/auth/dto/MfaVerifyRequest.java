package edi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(@NotBlank String mfaToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}
