package edi.auth.dto;

/** Cuerpo opcional de refresh/logout para clientes sin cookies (Postman, scripts). */
public record RefreshRequest(String refreshToken) {}
