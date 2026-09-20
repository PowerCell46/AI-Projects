package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortCodeGenerator;

import cn.hutool.core.util.IdUtil;

class SnowflakeShortCodeGeneratorTest {

    private static final int BATCH_SIZE = 10_000;
    private static final int THREAD_COUNT = 8;

    private final ShortCodeGenerator shortCodeGenerator = new SnowflakeShortCodeGenerator(IdUtil.getSnowflake(1, 1));

    @Test
    void should_generate_a_non_empty_code() {
        assertThat(shortCodeGenerator.generate()).isNotEmpty();
    }

    @Test
    void should_generate_unique_codes_across_a_large_single_threaded_batch() {
        Set<String> codes = IntStream.range(0, BATCH_SIZE)
                .mapToObj(i -> shortCodeGenerator.generate())
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(BATCH_SIZE);
    }

    @Test
    void should_generate_unique_codes_when_called_from_several_threads_concurrently() throws InterruptedException {
        Set<String> codes = ConcurrentHashMap.newKeySet();
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int thread = 0; thread < THREAD_COUNT; thread++) {
            executorService.execute(() -> {
                readyLatch.countDown();
                awaitUninterruptibly(startLatch);
                IntStream.range(0, BATCH_SIZE / THREAD_COUNT)
                        .forEach(i -> codes.add(shortCodeGenerator.generate()));
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(codes).hasSize(BATCH_SIZE);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
