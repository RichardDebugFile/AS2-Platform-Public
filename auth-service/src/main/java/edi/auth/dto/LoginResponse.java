package edi.auth.dto;

import java.util.Set;

/**
 * Respuesta de {@code /auth/login}, {@code /auth/mfa/verify} y {@code /auth/refresh}. Nunca lleva
 * los tokens: viajan en cookies HttpOnly (RN-02).
 *
 * <p>Los campos de sesion son la unica fuente de verdad para la consola: el aviso de inactividad y
 * el techo de la sesion salen de la configuracion del servidor, no de una copia en el cliente.</p>
 *
 * @param expiresIn          segundos de vida del access token
 * @param sessionExpiresAt   epoch ms del techo absoluto de la sesion (se hereda en cada refresh)
 * @param refreshExpiresAt   epoch ms hasta el que el servidor acepta renovar sin actividad
 * @param idleTimeoutSeconds inactividad maxima que el cliente debe vigilar
 * @param sessionMaxSeconds  vida maxima de la sesion desde el login
 */
public record LoginResponse(
        String username,
        String email,
        Set<String> roles,
        long expiresIn,
        long sessionExpiresAt,
        long refreshExpiresAt,
        long idleTimeoutSeconds,
        long sessionMaxSeconds) {}
