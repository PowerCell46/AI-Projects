package com.peter_gerdzhikov.twitter_api_gateway.configurations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

class MinioConfigurationTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);

    private static final Duration IO_TIMEOUT = Duration.ofMillis(300);

    private static final Duration GENEROUS_BOUND = Duration.ofSeconds(5);

    @Test
    void should_give_up_on_a_minio_that_accepts_the_connection_but_never_answers() throws IOException {
        try (ServerSocket stalledMinio = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            MinioClient client = new MinioConfiguration().minioClient(
                    "http://127.0.0.1:" + stalledMinio.getLocalPort(),
                    "access-key",
                    "secret-key-1234",
                    CONNECT_TIMEOUT,
                    IO_TIMEOUT
            );
            long startedAtNanos = System.nanoTime();

            assertThatThrownBy(() -> client.bucketExists(BucketExistsArgs.builder().bucket("any").build()))
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);

            Duration waited = Duration.ofNanos(System.nanoTime() - startedAtNanos);
            assertThat(waited).isLessThan(GENEROUS_BOUND);
        }
    }
}
