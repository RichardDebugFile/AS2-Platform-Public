package edi.gateway.alerts.dto;

import java.time.Instant;
import java.util.Map;

/** Evento de una llamada a la API. Debe coincidir con el DTO de alerts-service. */
public record IngestApiCallRequest(
        String traceId,
        String service,
        String method,
        String path,
        int status,
        long latencyMs,
        Instant timestamp,
        String clientIp,
        Map<String, Object> extra) {}
