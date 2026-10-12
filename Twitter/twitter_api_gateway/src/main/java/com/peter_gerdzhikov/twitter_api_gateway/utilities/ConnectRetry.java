package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.web.client.ResourceAccessException;

/**
 * Retries a downstream call while the connection itself is refused, so a service that is still
 * booting does not fail the caller. Only connection failures are retried: an answer from the
 * service, even an error status, returns or throws at once.
 */
public final class ConnectRetry {

    public static final int DEFAULT_MAX_CONNECT_ATTEMPTS = 36;

    public static final Duration DEFAULT_CONNECT_RETRY_DELAY = Duration.ofSeconds(5);

    private ConnectRetry() {
    }

    public static void runWithConnectRetry(Runnable call, String serviceName) {
        runWithConnectRetry(call, serviceName, DEFAULT_MAX_CONNECT_ATTEMPTS, DEFAULT_CONNECT_RETRY_DELAY);
    }

    public static void runWithConnectRetry(Runnable call, String serviceName, int maxAttempts, Duration retryDelay) {
        supplyWithConnectRetry(() -> {
            call.run();
            return null;
        }, serviceName, maxAttempts, retryDelay);
    }

    public static <T> T supplyWithConnectRetry(Supplier<T> call, String serviceName) {
        return supplyWithConnectRetry(call, serviceName, DEFAULT_MAX_CONNECT_ATTEMPTS, DEFAULT_CONNECT_RETRY_DELAY);
    }

    public static <T> T supplyWithConnectRetry(Supplier<T> call, String serviceName, int maxAttempts, Duration retryDelay) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();

            } catch (ResourceAccessException ex) {
                if (attempt == maxAttempts) {
                    throw ex;
                }

                waitBeforeRetry(serviceName, retryDelay);
            }
        }
    }

    private static void waitBeforeRetry(String serviceName, Duration retryDelay) {
        try {
            Thread.sleep(retryDelay);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + serviceName + ".", ex);
        }
    }
}
