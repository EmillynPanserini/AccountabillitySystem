package org.example.identidade.sessao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Limites locais por janela fixa; reservas impedem que autenticações simultâneas ultrapassem o teto. */
public final class LoginAttempts {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_BUCKETS = 10_000;
    private final Clock clock;
    private final Map<Key, Bucket> buckets = new HashMap<>();

    public LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    public synchronized Reservation reserve(String identifier, String ip) {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> bucket.inFlight == 0 && !now.isBefore(bucket.expires));
        Key pairKey = new Key(JdbcIdentityUsers.normalize(identifier), ip);
        Key ipKey = new Key(null, ip);
        Bucket pair = buckets.get(pairKey);
        Bucket address = buckets.get(ipKey);
        // Uma origem já limitada não pode consumir memória criando novos identificadores.
        if ((pair != null && pair.failures + pair.inFlight >= 5)
                || (address != null && address.failures + address.inFlight >= 30)) return null;
        int missing = (pair == null ? 1 : 0) + (address == null ? 1 : 0);
        if (buckets.size() + missing > MAX_BUCKETS) return null;
        pair = buckets.computeIfAbsent(pairKey, key -> new Bucket(now.plus(WINDOW)));
        address = buckets.computeIfAbsent(ipKey, key -> new Bucket(now.plus(WINDOW)));
        pair.inFlight++;
        address.inFlight++;
        return new Reservation(pair, address);
    }

    public synchronized void finish(Reservation reservation, boolean failed) {
        if (reservation.finished) return;
        reservation.finished = true;
        for (Bucket bucket : new Bucket[]{reservation.pair, reservation.address}) {
            bucket.inFlight--;
            if (failed) bucket.failures++;
        }
    }

    static final class Bucket {
        private final Instant expires;
        private int failures;
        private int inFlight;
        private Bucket(Instant expires) { this.expires = expires; }
    }

    public static final class Reservation {
        private final Bucket pair;
        private final Bucket address;
        private boolean finished;
        private Reservation(Bucket pair, Bucket address) { this.pair = pair; this.address = address; }
    }

    private record Key(String identifier, String ip) {}
}
