package com.nisuev.dips.gatewayservice.resilience;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ExponentialBackoffRetry {
    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final double jitter;
    private final Predicate<Throwable> retryOn;

    public ExponentialBackoffRetry(int maxAttempts, Duration initialDelay, double multiplier, double jitter,
                                   Predicate<Throwable> retryOn) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (multiplier < 1) {
            throw new IllegalArgumentException("multiplier must be >= 1");
        }
        if (jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("jitter must be in [0, 1]");
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.jitter = jitter;
        this.retryOn = retryOn;
    }

    public <T> T execute(Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts || !retryOn.test(e)) {
                    throw e;
                }
                pause(attempt);
            }
        }
    }

    public void run(Runnable call) {
        execute(() -> {
            call.run();
            return null;
        });
    }

    private Duration delayAfter(int failedAttempt) {
        double base = initialDelay.toMillis() * Math.pow(multiplier, failedAttempt - 1);
        double factor = 1 - jitter + 2 * jitter * ThreadLocalRandom.current().nextDouble();
        return Duration.ofMillis(Math.round(base * factor));
    }

    private void pause(int failedAttempt) {
        try {
            Thread.sleep(delayAfter(failedAttempt).toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", ie);
        }
    }
}
