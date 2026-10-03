package com.peter_gerdzhikov.twitter_timeline_service.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs tasks on their own threads and releases them all from one latch, so the calls really overlap.
 */
public final class LatchedTasks {

    private static final int TIMEOUT_SECONDS = 60;

    private LatchedTasks() {
    }

    public static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    return task.call();
                }));
            }

            ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return results;

        } finally {
            executor.shutdownNow();
        }
    }
}
