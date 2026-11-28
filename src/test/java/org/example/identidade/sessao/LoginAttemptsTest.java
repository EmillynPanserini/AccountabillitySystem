package org.example.identidade.sessao;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptsTest {
    @Test
    void limitsFailuresByPairAndExpiresAfterFifteenMinutes() {
        var clock = new MutableClock();
        var attempts = new LoginAttempts(clock);
        for (int index = 0; index < 5; index++) {
            var reservation = attempts.reserve(" Admin ", "127.0.0.1");
            assertNotNull(reservation);
            attempts.finish(reservation, true);
        }
        assertNull(attempts.reserve("admin", "127.0.0.1"));
        clock.advance(Duration.ofMinutes(15).minusMillis(1));
        assertNull(attempts.reserve("admin", "127.0.0.1"));
        clock.advance(Duration.ofMillis(1));
        assertNotNull(attempts.reserve("admin", "127.0.0.1"));
    }

    @Test
    void limitsThirtyFailuresPerIpAcrossIdentifiers() {
        var attempts = new LoginAttempts(Clock.systemUTC());
        for (int index = 0; index < 30; index++) {
            var reservation = attempts.reserve("user-" + index, "127.0.0.1");
            assertNotNull(reservation);
            attempts.finish(reservation, true);
        }
        assertNull(attempts.reserve("new-user", "127.0.0.1"));
        assertNotNull(attempts.reserve("new-user", "127.0.0.2"));
    }

    @Test
    void successReleasesReservationWithoutCountingFailureAndFinishIsIdempotent() {
        var attempts = new LoginAttempts(Clock.systemUTC());
        for (int index = 0; index < 40; index++) {
            var reservation = attempts.reserve("admin", "127.0.0.1");
            assertNotNull(reservation);
            attempts.finish(reservation, false);
            attempts.finish(reservation, true);
        }
        assertNotNull(attempts.reserve("admin", "127.0.0.1"));
    }

    @Test
    void concurrentReservationsCannotExceedFive() throws Exception {
        var attempts = new LoginAttempts(Clock.systemUTC());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(20)) {
            var futures = new ArrayList<java.util.concurrent.Future<LoginAttempts.Reservation>>();
            for (int index = 0; index < 20; index++) futures.add(executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return attempts.reserve("admin", "127.0.0.1");
            }));
            start.countDown();
            int reserved = 0;
            for (var future : futures) if (future.get(10, TimeUnit.SECONDS) != null) reserved++;
            assertEquals(5, reserved);
        }
    }

    @Test
    void boundedMemoryFailsClosedAndReclaimsExpiredEntries() {
        var clock = new MutableClock();
        var attempts = new LoginAttempts(clock);
        for (int index = 0; index < 5000; index++) {
            var reservation = attempts.reserve("user", "address-" + index);
            assertNotNull(reservation);
            attempts.finish(reservation, true);
        }
        assertNull(attempts.reserve("user", "another-address"));
        clock.advance(Duration.ofMinutes(15));
        assertNotNull(attempts.reserve("user", "another-address"));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-28T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
