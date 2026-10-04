package com.peter_gerdzhikov.twitter_api_gateway.controllers.likes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class LikeControllerIntegrationTest extends AbstractMinioIntegrationTest {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private RestTestClient restTestClient;

    @Nested
    class Like {

        @Test
        void should_return_204_when_the_tweet_id_is_a_uuid() {
            send(HttpMethod.PUT, UUID.randomUUID().toString())
                    .expectStatus()
                    .isNoContent();
        }

        @Test
        void should_return_204_when_the_tweet_is_liked_again() {
            String tweetId = UUID.randomUUID().toString();
            send(HttpMethod.PUT, tweetId)
                    .expectStatus()
                    .isNoContent();

            send(HttpMethod.PUT, tweetId)
                    .expectStatus()
                    .isNoContent();
        }

        @Test
        void should_return_400_when_the_tweet_id_is_not_a_uuid() {
            assertMalformedRequestParameter(send(HttpMethod.PUT, "not-a-uuid"));
        }
    }

    @Nested
    class Unlike {

        @Test
        void should_return_204_when_the_tweet_id_is_a_uuid() {
            send(HttpMethod.DELETE, UUID.randomUUID().toString())
                    .expectStatus()
                    .isNoContent();
        }

        @Test
        void should_return_204_when_the_tweet_is_unliked_again() {
            String tweetId = UUID.randomUUID().toString();
            send(HttpMethod.DELETE, tweetId)
                    .expectStatus()
                    .isNoContent();

            send(HttpMethod.DELETE, tweetId)
                    .expectStatus()
                    .isNoContent();
        }

        @Test
        void should_return_400_when_the_tweet_id_is_not_a_uuid() {
            assertMalformedRequestParameter(send(HttpMethod.DELETE, "not-a-uuid"));
        }
    }

    private void assertMalformedRequestParameter(RestTestClient.ResponseSpec response) {
        ErrorResponseDTO body = response
                .expectStatus()
                .isBadRequest()
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getMessages()).containsExactly("Malformed request parameter.");
    }

    private RestTestClient.ResponseSpec send(HttpMethod method, String tweetId) {
        return restTestClient
                .method(method)
                .uri("/api/v1/likes/{tweetId}", tweetId)
                .cookie(CookieFactory.COOKIE_NAME, TestJwts.sign(TestJwts.validClaims(), jwtSecret))
                .exchange();
    }
}
