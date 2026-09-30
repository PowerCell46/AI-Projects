package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.RecordingHttpServer;
import com.peter_gerdzhikov.twitter_api_gateway.support.RecordingHttpServer.RecordedRequest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

/**
 * Runs against a real servlet container: MockMvc never applies the multipart size limits or the body-size
 * filter to a parsed upload, so only a real HTTP request proves the gateway's own limits stay out of the way
 * of a tweet and still guard every other route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TweetBodyCapsIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String BOUNDARY = "tweet-body-boundary";

    private static final String TWEETS_PATH = "/api/v1/tweets";

    private static final String TWEET_PATH = TWEETS_PATH + "/6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

    private static final int PRIME_FILLER_PERIOD = 251;

    private static final RecordingHttpServer TWEET_SERVICE = RecordingHttpServer.start(HttpStatus.CREATED.value());

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.request.max-body-bytes}")
    private int maxBodyBytes;

    @Value("${app.request.max-tweet-body-bytes}")
    private int maxTweetBodyBytes;

    @Value("${app.request.max-upload-body-bytes}")
    private int maxUploadBodyBytes;

    @LocalServerPort
    private int port;

    @Autowired
    private RestTestClient restTestClient;

    @DynamicPropertySource
    static void pointTheRoutesAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.tweet-service.url", TWEET_SERVICE::baseUrl);
    }

    @BeforeEach
    void forgetEarlierRequests() {
        TWEET_SERVICE.reset();
    }

    @Nested
    class CreateTweet {

        @Test
        void should_forward_a_byte_identical_multipart_post_when_the_body_is_exactly_at_the_tweet_cap() {
            byte[] body = multipartBodyOfSize(maxTweetBodyBytes);

            postTweet(body)
                    .expectStatus().isCreated();

            assertThat(TWEET_SERVICE.requests())
                    .containsExactly(new RecordedRequest("POST", TWEETS_PATH, body.length, RecordingHttpServer.sha256Of(body)));
        }

        @Test
        void should_return_413_and_complete_nothing_when_a_chunked_post_is_one_byte_over_the_tweet_cap() throws Exception {
            assertThat(chunkedStatus("POST", TWEETS_PATH, maxTweetBodyBytes + 1)).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
            assertThat(TWEET_SERVICE.requests()).isEmpty();
        }

        @Test
        void should_return_413_and_forward_nothing_when_a_multipart_post_is_one_byte_over_the_tweet_cap() {
            postTweet(multipartBodyOfSize(maxTweetBodyBytes + 1))
                    .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

            assertThat(TWEET_SERVICE.requests()).isEmpty();
        }
    }

    @Nested
    class EditTweet {

        @Test
        void should_return_413_and_complete_nothing_when_a_chunked_put_body_is_over_8_kb() throws Exception {
            assertThat(chunkedStatus("PUT", TWEET_PATH, maxBodyBytes + 1)).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
            assertThat(TWEET_SERVICE.requests()).isEmpty();
        }

        @Test
        void should_return_413_and_forward_nothing_when_a_put_body_is_over_8_kb() {
            restTestClient.put()
                    .uri(TWEET_PATH)
                    .cookie(CookieFactory.COOKIE_NAME, cookie())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new byte[maxBodyBytes + 1])
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

            assertThat(TWEET_SERVICE.requests()).isEmpty();
        }
    }

    @Nested
    class PictureRoutes {

        @Test
        void should_keep_the_5_mb_cap_on_the_profile_picture_routes_when_the_tweet_cap_is_raised() {
            int largerThanTheUploadCapButUnderTheTweetCap = maxUploadBodyBytes + 1;

            restTestClient.put()
                    .uri("/api/v1/users/me/profile-picture")
                    .cookie(CookieFactory.COOKIE_NAME, cookie())
                    .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                    .body(multipartBodyOfSize(largerThanTheUploadCapButUnderTheTweetCap))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }
    }

    private RestTestClient.ResponseSpec postTweet(byte[] body) {
        return restTestClient.post()
                .uri(TWEETS_PATH)
                .cookie(CookieFactory.COOKIE_NAME, cookie())
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                .body(body)
                .exchange();
    }

    /**
     * Sent through the JDK client with an unknown length so it goes out chunked, which the declared-length
     * check cannot see. The downstream records a request only once its body arrives whole, so an aborted
     * one leaves no record.
     */
    private int chunkedStatus(String method, String path, int bodyBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(new byte[bodyBytes])))
                .header("Cookie", CookieFactory.COOKIE_NAME + "=" + cookie())
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .build();

        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private String cookie() {
        return TestJwts.sign(TestJwts.validClaims().subject(UUID.randomUUID().toString()), jwtSecret);
    }

    /**
     * Built by hand so the request carries a {@code Content-Length}; a streamed multipart body goes out
     * chunked, which would bypass the declared-length check this test exists to exercise. The filler is a
     * repeating non-uniform pattern so a dropped or reordered chunk changes the bytes.
     */
    private byte[] multipartBodyOfSize(int totalBytes) {
        byte[] head = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"images\"; filename=\"photo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[totalBytes];
        System.arraycopy(head, 0, body, 0, head.length);
        for (int i = head.length; i < totalBytes - tail.length; i++) {
            body[i] = (byte) (i % PRIME_FILLER_PERIOD);
        }

        System.arraycopy(tail, 0, body, totalBytes - tail.length, tail.length);

        return body;
    }
}
