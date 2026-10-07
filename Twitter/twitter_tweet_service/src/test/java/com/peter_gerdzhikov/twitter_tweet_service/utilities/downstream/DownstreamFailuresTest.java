package com.peter_gerdzhikov.twitter_tweet_service.utilities.downstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException;

class DownstreamFailuresTest {

    @Test
    void should_translate_a_read_timeout_to_an_upstream_timeout() {
        ResourceAccessException failure = new ResourceAccessException("I/O error", new HttpTimeoutException("timed out"));

        assertThat(DownstreamFailures.translate(failure))
                .isInstanceOf(UpstreamTimeoutException.class)
                .hasCause(failure);
    }

    @Test
    void should_translate_a_connect_timeout_to_upstream_unavailable() {
        ResourceAccessException failure =
                new ResourceAccessException("I/O error", new HttpConnectTimeoutException("connect timed out"));

        assertThat(DownstreamFailures.translate(failure)).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_translate_a_refused_connection_to_upstream_unavailable() {
        ResourceAccessException failure = new ResourceAccessException("I/O error", new ConnectException("refused"));

        assertThat(DownstreamFailures.translate(failure)).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_translate_a_server_error_status_to_upstream_unavailable() {
        HttpServerErrorException failure = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, "secret detail".getBytes(), null);

        assertThat(DownstreamFailures.translate(failure)).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_translate_a_client_error_status_to_upstream_unavailable() {
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, new byte[0], null);

        assertThat(DownstreamFailures.translate(failure)).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_translate_an_unreadable_answer_to_upstream_unavailable() {
        assertThat(DownstreamFailures.translate(new RestClientException("Error while extracting response")))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void should_never_put_what_the_downstream_said_in_the_exception_message() {
        HttpServerErrorException failure = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", null, "secret detail".getBytes(), null);

        assertThat(DownstreamFailures.translate(failure).getMessage()).isEqualTo("Upstream service unavailable.");
    }
}
