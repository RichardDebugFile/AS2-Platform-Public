package edi.partners.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que avanza un minuto en cada lectura: cada evento tiene su propio instante. */
public final class TickingClock extends Clock {

    private Instant now = Instant.parse("2026-10-20T12:00:00Z");

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public synchronized Instant instant() {
        now = now.plus(Duration.ofMinutes(1));
        return now;
    }
}
