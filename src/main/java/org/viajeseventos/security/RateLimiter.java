package org.viajeseventos.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request counter, keyed by caller-supplied string (typically {@code route + ":" + ip}).
 * In-memory and per-instance — fine for a single JVM; horizontally scaling this process would need a
 * shared store (Redis, etc.) instead, since each instance would otherwise enforce its own limit.
 */
public final class RateLimiter {

    private record Window(long startMs, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMs;

    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /** Returns true if the caller is still within the limit for this window. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs() >= windowMs) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() <= maxRequests;
    }

    /** Drops windows old enough that they'd reset on next access anyway — bounds memory over time. */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().startMs() >= windowMs);
    }
}
