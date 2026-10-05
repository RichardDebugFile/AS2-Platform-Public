package edi.auth.dto;

/** Datos para registrar la cuenta en la app autenticadora. El secreto solo va dentro del QR/URI. */
public record MfaSetupResponse(String otpauthUrl, String secretMasked, String qrPngBase64) {}
