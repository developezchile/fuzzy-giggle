package org.viajeseventos.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRejects() {
        RateLimiter limiter = new RateLimiter(3, 60_000);
        String key = "login:1.2.3.4";

        assertTrue(limiter.tryAcquire(key));
        assertTrue(limiter.tryAcquire(key));
        assertTrue(limiter.tryAcquire(key));
        assertFalse(limiter.tryAcquire(key));
        assertFalse(limiter.tryAcquire(key));
    }

    @Test
    void tracksDifferentKeysIndependently() {
        RateLimiter limiter = new RateLimiter(1, 60_000);

        assertTrue(limiter.tryAcquire("login:1.2.3.4"));
        assertTrue(limiter.tryAcquire("register:1.2.3.4"));
        assertTrue(limiter.tryAcquire("login:5.6.7.8"));
        assertFalse(limiter.tryAcquire("login:1.2.3.4"));
    }

    @Test
    void resetsAfterTheWindowElapses() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 100);
        String key = "login:1.2.3.4";

        assertTrue(limiter.tryAcquire(key));
        assertFalse(limiter.tryAcquire(key));

        Thread.sleep(150);

        assertTrue(limiter.tryAcquire(key));
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() throws InterruptedException {
        int max = 20;
        RateLimiter limiter = new RateLimiter(max, 60_000);
        String key = "register:9.9.9.9";
        int callers = 100;

        var executor = java.util.concurrent.Executors.newFixedThreadPool(callers);
        var latch = new java.util.concurrent.CountDownLatch(callers);
        var successCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < callers; i++) {
            executor.submit(() -> {
                if (limiter.tryAcquire(key)) successCount.incrementAndGet();
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        assertEquals(max, successCount.get());
    }
}
