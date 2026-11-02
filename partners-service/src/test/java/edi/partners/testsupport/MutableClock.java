package edi.partners.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba fijo que la prueba puede mover (p. ej. al dia siguiente del vencimiento). */
public final class MutableClock extends Clock {

    private Instant now;

    public MutableClock(String now) {
        this.now = Instant.parse(now);
    }

    public void set(String instant) {
        this.now = Instant.parse(instant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
