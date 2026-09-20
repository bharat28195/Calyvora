package com.calyvora.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How many requests one caller may make, and what is left.
 *
 * <p>A token bucket rather than a fixed window: a window resets on the clock, so a caller who spends
 * their whole allowance in the last second of one window and the first second of the next gets twice
 * the limit in two seconds and then nothing for a minute. A bucket refills continuously, which both
 * tolerates the burst a page load actually is — a screen opening six endpoints at once is normal —
 * and still holds the sustained rate to the limit.
 *
 * <p><b>This is per process, and that is a deliberate limit rather than an oversight.</b> The product
 * runs as one instance, so per-process and global are the same number today. Run two and each gets
 * its own buckets, and the effective limit doubles. That is still far better than none, and the
 * alternative — a shared counter in Postgres or Redis — is a round trip on every single request and a
 * new thing that can be down. Revisit it when there is a second instance, not before.
 *
 * <p>Buckets are held in a map keyed by caller. That map is itself an attack surface: a caller who
 * varies their key every request would grow it without bound, so idle buckets are swept out and the
 * map has a hard ceiling above which new keys are simply let through rather than remembered. Letting
 * an unusual caller past is a much smaller problem than exhausting the heap defending against them.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Above this many distinct callers we stop tracking new ones until the sweep catches up.
     *
     * <p>Sized for far more simultaneous callers than this product has, so reaching it means either a
     * deliberate attempt to exhaust the map or a bug in how keys are built — both of which are worth
     * a log line.
     */
    static final int MAX_TRACKED = 50_000;

    /** Buckets untouched for this long are forgotten; a full bucket is indistinguishable from none. */
    private static final Duration IDLE_TTL = Duration.ofMinutes(10);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt = new AtomicLong(0);
    private volatile boolean warnedAboutSize = false;

    /** What a caller may do, and how much of it is left. */
    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
    }

    /**
     * Take one token for {@code key}.
     *
     * @param permitsPerMinute the sustained rate; also the burst, since a full bucket holds a minute
     */
    public Decision take(String key, int permitsPerMinute) {
        if (permitsPerMinute <= 0) {
            return new Decision(true, permitsPerMinute, permitsPerMinute, 0);
        }
        long now = System.nanoTime();
        sweepIfDue(now);

        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= MAX_TRACKED) {
                warnOnce();
                return new Decision(true, permitsPerMinute, permitsPerMinute, 0);
            }
            bucket = buckets.computeIfAbsent(key, k -> new Bucket(permitsPerMinute, now));
        }
        return bucket.take(permitsPerMinute, now);
    }

    /**
     * Give one token back.
     *
     * <p>Used when an attempt turns out to have been legitimate. The strict budget on the auth surface
     * exists to slow down guessing, and a correct password is not a guess — without this, forty people
     * arriving at nine o'clock from one office, or a load test signing in twenty-five valid users at
     * once, are indistinguishable from an attack and get locked out for being right.
     *
     * <p>One token rather than clearing the bucket: somebody who holds one valid account would
     * otherwise be able to reset their own allowance at will and go on guessing at other people's.
     */
    public void refund(String key, int permitsPerMinute) {
        Bucket bucket = buckets.get(key);
        if (bucket != null) {
            bucket.refund(permitsPerMinute);
        }
    }

    int tracked() {
        return buckets.size();
    }

    /** Forget every caller. For tests, which need each one to start from a known allowance. */
    void reset() {
        buckets.clear();
    }

    private void warnOnce() {
        if (!warnedAboutSize) {
            warnedAboutSize = true;
            log.warn("Rate limiter is tracking {} callers, its ceiling. New callers are not being "
                    + "limited until this falls. This is either an attempt to exhaust it or a bug in "
                    + "how its keys are built.", MAX_TRACKED);
        }
    }

    /**
     * Drop idle buckets, at most once a minute and on whichever request happens to arrive first.
     *
     * <p>A scheduled task would be tidier, but this runs in the request that needs the room and costs
     * nothing on every other one.
     */
    private void sweepIfDue(long now) {
        long due = nextSweepAt.get();
        if (now < due || !nextSweepAt.compareAndSet(due, now + Duration.ofMinutes(1).toNanos())) {
            return;
        }
        long cutoff = now - IDLE_TTL.toNanos();
        buckets.entrySet().removeIf(e -> e.getValue().lastSeenBefore(cutoff));
    }

    /**
     * One caller's allowance.
     *
     * <p>Synchronised rather than lock-free: the critical section is a subtraction, contention is per
     * caller rather than global, and a correct simple version beats a clever one that miscounts under
     * load — which for a rate limiter means either locking out a paying customer or not limiting at
     * all.
     */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        Bucket(int permitsPerMinute, long now) {
            this.tokens = permitsPerMinute;
            this.lastRefillNanos = now;
        }

        synchronized Decision take(int permitsPerMinute, long now) {
            double perNano = permitsPerMinute / (double) Duration.ofMinutes(1).toNanos();
            tokens = Math.min(permitsPerMinute, tokens + (now - lastRefillNanos) * perNano);
            lastRefillNanos = now;

            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, permitsPerMinute, (int) tokens, 0);
            }
            // Round up: a Retry-After of zero invites an immediate retry that is certain to fail.
            long waitSeconds = (long) Math.ceil((1 - tokens) / (permitsPerMinute / 60.0));
            return new Decision(false, permitsPerMinute, 0, Math.max(1, waitSeconds));
        }

        synchronized void refund(int permitsPerMinute) {
            tokens = Math.min(permitsPerMinute, tokens + 1);
        }

        synchronized boolean lastSeenBefore(long cutoff) {
            return lastRefillNanos < cutoff;
        }
    }
}
