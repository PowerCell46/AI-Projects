package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestImages;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TweetControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String TWEETS_PATH = "/api/v1/tweets";

    private static final String USER_ID_HEADER = "X-User-Id";

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.request.max-tweet-body-bytes}")
    private int maxTweetBodyBytes;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Nested
    class Identity {

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_tweet_service.controllers.TweetControllerIntegrationTest#endpoints")
        void should_return_400_when_the_user_id_header_is_missing(String endpoint) throws Exception {
            mockMvc
                    .perform(requestFor(endpoint))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_tweet_service.controllers.TweetControllerIntegrationTest#endpoints")
        void should_return_400_when_the_user_id_header_is_not_a_uuid(String endpoint) throws Exception {
            mockMvc
                    .perform(requestFor(endpoint).header(USER_ID_HEADER, "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_200_when_health_is_requested_without_the_user_id_header() throws Exception {
            mockMvc
                    .perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        void should_answer_with_the_apps_error_message_when_the_user_id_header_is_missing() throws Exception {
            MvcResult result = mockMvc
                    .perform(multipart(HttpMethod.POST, TWEETS_PATH))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString())
                    .isEqualTo("Missing or invalid caller identity.");
        }
    }

    @Nested
    class CreateTweet {

        @Test
        void should_return_201_and_store_the_tweet_and_a_pending_event_when_the_text_is_valid() throws Exception {
            UUID authorId = UUID.randomUUID();

            JsonNode body = create(authorId, "  hello world  ");

            UUID tweetId = UUID.fromString(body.get("id").asString());
            assertThat(body.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(body.get("content").asString()).isEqualTo("hello world");
            assertThat(body.get("views").asLong()).isZero();
            assertThat(body.get("images")).isEmpty();
            assertThat(body.get("createdAt").asString()).isEqualTo(body.get("updatedAt").asString());
            Tweet stored = tweetRepository.findById(tweetId).orElseThrow();
            assertThat(stored.getAuthorId()).isEqualTo(authorId);
            assertThat(stored.getContent()).isEqualTo("hello world");
            assertThat(stored.getImages()).isEmpty();
            OutboxMessage message = outboxMessageFor(tweetId);
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getTopic()).isEqualTo("tweet.created");
            JsonNode event = objectMapper.readTree(message.getPayload());
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("tweetId").asString()).isEqualTo(tweetId.toString());
            assertThat(event.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(event.get("content").asString()).isEqualTo("hello world");
            assertThat(event.get("createdAt").asString()).isEqualTo(body.get("createdAt").asString());
            assertThat(event.get("imageIds")).isEmpty();
        }

        @Test
        void should_return_201_and_store_the_object_in_minio_when_one_image_is_attached() throws Exception {
            JsonNode body = create(UUID.randomUUID(), "with image", image("a.png", TestImages.png()));

            Tweet stored = storedTweet(body);
            assertThat(stored.getImages()).hasSize(1);
            TweetImage image = stored.getImages().get(0);
            assertThat(contentTypeOf(image.getObjectKey())).isEqualTo("image/png");
            assertThat(image.getContentType()).isEqualTo("image/png");
            assertThat(image.getSizeBytes()).isEqualTo(TestImages.png().length);
            assertThat(imageIdsInEvent(stored)).containsExactly(image.getId().toString());
        }

        @Test
        void should_return_201_and_keep_the_upload_order_when_four_mixed_images_are_attached() throws Exception {
            JsonNode body = create(
                    UUID.randomUUID(),
                    "four",
                    image("a.jpg", TestImages.jpeg()),
                    image("b.png", TestImages.png()),
                    image("c.webp", TestImages.webp()),
                    image("d.png", TestImages.png()));

            Tweet stored = storedTweet(body);
            List<String> types = stored
                    .getImages()
                    .stream()
                    .map(TweetImage::getContentType)
                    .toList();
            assertThat(types).containsExactly("image/jpeg", "image/png", "image/webp", "image/png");
            for (TweetImage image : stored.getImages()) {
                assertThat(contentTypeOf(image.getObjectKey())).isEqualTo(image.getContentType());
            }
            assertThat(imageIdsInEvent(stored)).containsExactlyElementsOf(
                    stored.getImages().stream().map(image -> image.getId().toString()).toList());
            assertThat(body.get("images")).hasSize(4);
        }

        @Test
        void should_return_201_with_empty_content_when_only_images_are_attached() throws Exception {
            MvcResult result = mockMvc
                    .perform(createRequest(UUID.randomUUID()).file(image("a.png", TestImages.png())))
                    .andExpect(status().isCreated())
                    .andReturn();

            assertThat(json(result).get("content").asString()).isEmpty();
        }

        @Test
        void should_return_201_with_empty_content_when_the_text_is_blank_and_an_image_is_attached() throws Exception {
            JsonNode body = create(UUID.randomUUID(), "   ", image("a.png", TestImages.png()));

            assertThat(body.get("content").asString()).isEmpty();
        }

        @Test
        void should_return_400_when_there_is_no_text_and_no_image() throws Exception {
            UUID authorId = UUID.randomUUID();

            MvcResult result = mockMvc
                    .perform(createRequest(authorId))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("A tweet needs text or an image.");
            assertThat(tweetsBy(authorId)).isEmpty();
        }

        @Test
        void should_return_400_when_the_text_is_whitespace_only_and_there_is_no_image() throws Exception {
            UUID authorId = UUID.randomUUID();

            mockMvc
                    .perform(createRequest(authorId).param("content", " \t\n "))
                    .andExpect(status().isBadRequest());

            assertThat(tweetsBy(authorId)).isEmpty();
        }

        @Test
        void should_return_201_when_the_text_is_280_code_points() throws Exception {
            JsonNode body = create(UUID.randomUUID(), "a".repeat(280));

            assertThat(body.get("content").asString()).hasSize(280);
        }

        @Test
        void should_return_400_when_the_text_is_281_code_points() throws Exception {
            UUID authorId = UUID.randomUUID();

            MvcResult result = mockMvc
                    .perform(createRequest(authorId).param("content", "a".repeat(281)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString())
                    .isEqualTo("A tweet can be at most 280 characters.");
            assertThat(tweetsBy(authorId)).isEmpty();
        }

        @Test
        void should_return_201_when_the_text_is_280_emoji() throws Exception {
            String emoji = "\uD83D\uDE00".repeat(280);

            JsonNode body = create(UUID.randomUUID(), emoji);

            assertThat(body.get("content").asString()).isEqualTo(emoji);
        }

        @Test
        void should_return_400_and_store_nothing_when_five_images_are_attached() throws Exception {
            UUID authorId = UUID.randomUUID();
            long objectsBefore = objectCount();
            MockMultipartHttpServletRequestBuilder request = createRequest(authorId).param("content", "five");
            for (int i = 0; i < 5; i++) {
                request.file(image("a" + i + ".png", TestImages.png()));
            }

            MvcResult result = mockMvc
                    .perform(request)
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("A tweet can have at most 4 images.");
            assertThat(tweetsBy(authorId)).isEmpty();
            assertThat(objectCount()).isEqualTo(objectsBefore);
        }

        @Test
        void should_return_415_and_store_no_image_when_one_of_the_images_is_a_renamed_non_image() throws Exception {
            UUID authorId = UUID.randomUUID();
            long objectsBefore = objectCount();

            MvcResult result = mockMvc
                    .perform(createRequest(authorId)
                            .param("content", "renamed")
                            .file(image("valid.png", TestImages.png()))
                            .file(image("fake.png", TestImages.notAnImage())))
                    .andExpect(status().isUnsupportedMediaType())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("Unsupported image type.");
            assertThat(tweetsBy(authorId)).isEmpty();
            assertThat(objectCount()).isEqualTo(objectsBefore);
        }

        @Test
        void should_return_413_when_an_image_is_over_5_mb() throws Exception {
            UUID authorId = UUID.randomUUID();
            long objectsBefore = objectCount();

            mockMvc
                    .perform(createRequest(authorId)
                            .param("content", "big")
                            .file(image("big.jpg", TestImages.oversizedJpeg())))
                    .andExpect(status().isPayloadTooLarge());

            assertThat(tweetsBy(authorId)).isEmpty();
            assertThat(objectCount()).isEqualTo(objectsBefore);
        }

        @Test
        void should_return_400_when_an_image_part_is_empty() throws Exception {
            UUID authorId = UUID.randomUUID();

            MvcResult result = mockMvc
                    .perform(createRequest(authorId)
                            .param("content", "empty")
                            .file(image("empty.png", new byte[0])))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(json(result).get("messages").get(0).asString()).isEqualTo("The uploaded file is empty.");
            assertThat(tweetsBy(authorId)).isEmpty();
        }

        @Test
        void should_return_413_when_the_whole_body_is_over_the_tweet_cap() throws Exception {
            UUID authorId = UUID.randomUUID();

            mockMvc
                    .perform(post(TWEETS_PATH)
                            .header(USER_ID_HEADER, authorId.toString())
                            .contentType("multipart/form-data; boundary=cap-boundary")
                            .content(new byte[maxTweetBodyBytes + 1]))
                    .andExpect(status().isPayloadTooLarge());

            assertThat(tweetsBy(authorId)).isEmpty();
        }

        @Test
        void should_return_415_when_the_body_is_json_instead_of_multipart() throws Exception {
            mockMvc
                    .perform(post(TWEETS_PATH)
                            .header(USER_ID_HEADER, UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\": \"hello\"}"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void should_not_expose_the_object_key_when_the_tweet_is_created() throws Exception {
            MvcResult result = mockMvc
                    .perform(createRequest(UUID.randomUUID())
                            .param("content", "keys")
                            .file(image("a.png", TestImages.png())))
                    .andExpect(status().isCreated())
                    .andReturn();

            Tweet stored = storedTweet(json(result));
            String responseBody = result.getResponse().getContentAsString();
            assertThat(responseBody)
                    .doesNotContain("objectKey")
                    .doesNotContain(stored.getImages().get(0).getObjectKey());
        }
    }

    @Nested
    class GetTweet {

        @Test
        void should_return_200_with_the_body_shape_when_the_tweet_exists() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "shape", image("a.png", TestImages.png()));

            JsonNode body = getTweet(created.get("id").asString(), UUID.randomUUID(), status().isOk());

            assertThat(body.get("id").asString()).isEqualTo(created.get("id").asString());
            assertThat(body.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(body.get("content").asString()).isEqualTo("shape");
            assertThat(body.get("images")).hasSize(1);
            assertThat(body.get("images").get(0).get("id").asString())
                    .isEqualTo(created.get("images").get(0).get("id").asString());
            assertThat(body.get("images").get(0).get("contentType").asString()).isEqualTo("image/png");
            assertThat(body.get("images").get(0).get("sizeBytes").asLong()).isEqualTo(TestImages.png().length);
            assertThat(body.get("createdAt").asString()).isEqualTo(created.get("createdAt").asString());
            assertThat(body.get("updatedAt").asString()).isEqualTo(created.get("updatedAt").asString());
            assertThat(body.get("images").get(0).has("objectKey")).isFalse();
        }

        @Test
        void should_count_each_get_as_a_view_when_the_tweet_is_read_repeatedly() throws Exception {
            String id = create(UUID.randomUUID(), "views").get("id").asString();

            long first = getTweet(id, UUID.randomUUID(), status().isOk()).get("views").asLong();
            long second = getTweet(id, UUID.randomUUID(), status().isOk()).get("views").asLong();

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(2);
        }

        @Test
        void should_count_the_view_when_the_author_reads_their_own_tweet() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "mine").get("id").asString();

            JsonNode body = getTweet(id, authorId, status().isOk());

            assertThat(body.get("views").asLong()).isEqualTo(1);
        }

        @Test
        void should_leave_updated_at_unchanged_when_the_tweet_is_viewed() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "still");
            String id = created.get("id").asString();

            getTweet(id, UUID.randomUUID(), status().isOk());
            JsonNode body = getTweet(id, UUID.randomUUID(), status().isOk());

            assertThat(body.get("updatedAt").asString()).isEqualTo(created.get("updatedAt").asString());
            assertThat(tweetRepository.findById(UUID.fromString(id)).orElseThrow().getUpdatedAt())
                    .isEqualTo(tweetRepository.findById(UUID.fromString(id)).orElseThrow().getCreatedAt());
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            JsonNode body = getTweet(UUID.randomUUID().toString(), UUID.randomUUID(), status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Tweet not found.");
        }

        @Test
        void should_return_400_when_the_id_is_not_a_uuid() throws Exception {
            getTweet("not-a-uuid", UUID.randomUUID(), status().isBadRequest());
        }
    }

    @Nested
    class GetTweetImage {

        @Test
        void should_return_200_with_the_uploaded_bytes_and_headers_when_the_image_exists() throws Exception {
            byte[] jpeg = TestImages.jpeg();
            JsonNode created = create(UUID.randomUUID(), "bytes", image("a.jpg", jpeg));

            MvcResult result = mockMvc
                    .perform(getImage(created, 0))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(jpeg);
            assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg");
            assertThat(result.getResponse().getContentLength()).isEqualTo(jpeg.length);
            assertThat(result.getResponse().getHeader("Cache-Control"))
                    .contains("private", "max-age=31536000", "immutable");
            assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            mockMvc
                    .perform(get(TWEETS_PATH + "/" + UUID.randomUUID() + "/images/" + UUID.randomUUID())
                            .header(USER_ID_HEADER, UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_return_404_when_the_image_belongs_to_another_tweet() throws Exception {
            JsonNode withImage = create(UUID.randomUUID(), "owner", image("a.png", TestImages.png()));
            JsonNode other = create(UUID.randomUUID(), "other", image("b.png", TestImages.png()));
            String otherTweetsImageId = other.get("images").get(0).get("id").asString();

            mockMvc
                    .perform(get(TWEETS_PATH + "/" + withImage.get("id").asString() + "/images/" + otherTweetsImageId)
                            .header(USER_ID_HEADER, UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_not_change_views_when_an_image_is_fetched() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "quiet", image("a.png", TestImages.png()));

            mockMvc
                    .perform(getImage(created, 0))
                    .andExpect(status().isOk());

            assertThat(storedTweet(created).getViews()).isZero();
        }
    }

    @Nested
    class UpdateTweet {

        @AfterEach
        void resetClock() {
            mutableClock.reset();
        }

        @Test
        void should_return_200_and_update_the_content_when_the_author_edits() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "before", image("a.png", TestImages.png()));
            String id = created.get("id").asString();
            getTweet(id, authorId, status().isOk());
            mutableClock.advance(Duration.ofHours(1));

            JsonNode body = update(id, authorId, "  after  ", status().isOk());

            assertThat(body.get("content").asString()).isEqualTo("after");
            assertThat(Instant.parse(body.get("updatedAt").asString()))
                    .isEqualTo(Instant.parse(body.get("createdAt").asString()).plus(Duration.ofHours(1)));
            assertThat(body.get("images")).isEqualTo(created.get("images"));
            assertThat(body.get("views").asLong()).isEqualTo(1);
            assertThat(storedTweet(created).getContent()).isEqualTo("after");
        }

        @Test
        void should_not_write_an_outbox_message_when_the_content_is_edited() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "before").get("id").asString();

            update(id, authorId, "after", status().isOk());

            assertThat(outboxMessagesFor(UUID.fromString(id))).hasSize(1);
        }

        @Test
        void should_return_200_when_the_content_is_blank_and_the_tweet_has_images() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "before", image("a.png", TestImages.png())).get("id").asString();

            JsonNode body = update(id, authorId, "   ", status().isOk());

            assertThat(body.get("content").asString()).isEmpty();
        }

        @Test
        void should_return_400_when_the_content_is_blank_and_the_tweet_has_no_images() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "before").get("id").asString();

            JsonNode body = update(id, authorId, "   ", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("A tweet needs text or an image.");
            assertThat(tweetRepository.findById(UUID.fromString(id)).orElseThrow().getContent()).isEqualTo("before");
        }

        @Test
        void should_return_400_when_the_content_is_281_code_points() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "before").get("id").asString();

            update(id, authorId, "a".repeat(281), status().isBadRequest());

            assertThat(tweetRepository.findById(UUID.fromString(id)).orElseThrow().getContent()).isEqualTo("before");
        }

        @Test
        void should_return_403_and_leave_the_tweet_unchanged_when_someone_else_edits() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "before");
            Tweet before = storedTweet(created);

            JsonNode body = update(created.get("id").asString(), UUID.randomUUID(), "hijacked", status().isForbidden());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("You can only edit your own tweets.");
            Tweet after = storedTweet(created);
            assertThat(after.getContent()).isEqualTo("before");
            assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            update(UUID.randomUUID().toString(), UUID.randomUUID(), "x", status().isNotFound());
        }

        @Test
        void should_return_413_when_the_body_is_over_8_kb() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "before").get("id").asString();

            update(id, authorId, "a".repeat(9000), status().isPayloadTooLarge());

            assertThat(tweetRepository.findById(UUID.fromString(id)).orElseThrow().getContent()).isEqualTo("before");
        }
    }

    @Nested
    class DeleteTweet {

        @Test
        void should_return_204_and_remove_the_tweet_and_its_images_when_the_author_deletes() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(
                    authorId, "bye", image("a.png", TestImages.png()), image("b.jpg", TestImages.jpeg()));
            List<String> objectKeys = storedTweet(created)
                    .getImages()
                    .stream()
                    .map(TweetImage::getObjectKey)
                    .toList();

            delete(created.get("id").asString(), authorId, status().isNoContent());

            assertThat(tweetRepository.findById(UUID.fromString(created.get("id").asString()))).isEmpty();
            for (String objectKey : objectKeys) {
                assertThat(objectExists(objectKey)).isFalse();
            }
        }

        @Test
        void should_write_one_pending_deleted_event_when_the_author_deletes() throws Exception {
            UUID authorId = UUID.randomUUID();
            UUID tweetId = UUID.fromString(create(authorId, "bye").get("id").asString());

            delete(tweetId.toString(), authorId, status().isNoContent());

            List<OutboxMessage> messages = outboxMessagesFor(tweetId)
                    .stream()
                    .filter(message -> "tweet.deleted".equals(message.getTopic()))
                    .toList();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
            JsonNode event = objectMapper.readTree(messages.get(0).getPayload());
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("tweetId").asString()).isEqualTo(tweetId.toString());
            assertThat(event.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(Instant.parse(event.get("deletedAt").asString())).isEqualTo(mutableClock.instant());
        }

        @Test
        void should_return_403_and_change_nothing_when_someone_else_deletes() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "mine", image("a.png", TestImages.png()));
            UUID tweetId = UUID.fromString(created.get("id").asString());
            String objectKey = storedTweet(created).getImages().get(0).getObjectKey();
            int messagesBefore = outboxMessagesFor(tweetId).size();

            JsonNode body = delete(tweetId.toString(), UUID.randomUUID(), status().isForbidden());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("You can only delete your own tweets.");
            assertThat(tweetRepository.findById(tweetId)).isPresent();
            assertThat(objectExists(objectKey)).isTrue();
            assertThat(outboxMessagesFor(tweetId)).hasSize(messagesBefore);
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            JsonNode body = delete(UUID.randomUUID().toString(), UUID.randomUUID(), status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Tweet not found.");
        }

        @Test
        void should_return_404_and_write_no_second_event_when_the_tweet_is_deleted_twice() throws Exception {
            UUID authorId = UUID.randomUUID();
            UUID tweetId = UUID.fromString(create(authorId, "twice").get("id").asString());
            delete(tweetId.toString(), authorId, status().isNoContent());
            int messagesAfterFirst = outboxMessagesFor(tweetId).size();

            delete(tweetId.toString(), authorId, status().isNotFound());

            assertThat(outboxMessagesFor(tweetId)).hasSize(messagesAfterFirst);
        }

        @Test
        void should_return_404_when_the_deleted_tweet_is_read() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "gone").get("id").asString();
            delete(id, authorId, status().isNoContent());

            getTweet(id, authorId, status().isNotFound());
        }

        @Test
        void should_return_404_when_the_deleted_tweets_image_is_read() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "gone", image("a.png", TestImages.png()));
            delete(created.get("id").asString(), authorId, status().isNoContent());

            mockMvc
                    .perform(getImage(created, 0))
                    .andExpect(status().isNotFound());
        }
    }

    static Stream<String> endpoints() {
        return Stream.of(
                "POST /api/v1/tweets",
                "GET /api/v1/tweets/{id}",
                "GET /api/v1/tweets/{id}/images/{imageId}",
                "PUT /api/v1/tweets/{id}",
                "DELETE /api/v1/tweets/{id}"
        );
    }

    private AbstractMockHttpServletRequestBuilder<?> requestFor(String endpoint) {
        String[] methodAndPath = endpoint.split(" ");
        HttpMethod method = HttpMethod.valueOf(methodAndPath[0]);
        if (HttpMethod.POST.equals(method)) {
            return multipart(method, methodAndPath[1]);
        }

        String path = methodAndPath[1]
                .replace("{id}", UUID.randomUUID().toString())
                .replace("{imageId}", UUID.randomUUID().toString());

        return request(method, path);
    }

    private JsonNode delete(String id, UUID callerId, ResultMatcher expected) throws Exception {
        MvcResult result = mockMvc
                .perform(MockMvcRequestBuilders.delete(TWEETS_PATH + "/" + id).header(USER_ID_HEADER, callerId.toString()))
                .andExpect(expected)
                .andReturn();
        String body = result.getResponse().getContentAsString();

        return body.isEmpty() ? null : objectMapper.readTree(body);
    }

    private boolean objectExists(String objectKey) throws Exception {
        try {
            contentTypeOf(objectKey);

            return true;

        } catch (ErrorResponseException e) {
            return false;
        }
    }

    private JsonNode update(String id, UUID callerId, String content, ResultMatcher expected) throws Exception {
        return json(mockMvc
                .perform(put(TWEETS_PATH + "/" + id)
                        .header(USER_ID_HEADER, callerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(expected)
                .andReturn());
    }

    private JsonNode getTweet(String id, UUID callerId, ResultMatcher expected) throws Exception {
        return json(mockMvc
                .perform(get(TWEETS_PATH + "/" + id).header(USER_ID_HEADER, callerId.toString()))
                .andExpect(expected)
                .andReturn());
    }

    private MockHttpServletRequestBuilder getImage(JsonNode createdTweet, int imageIndex) {
        return get(TWEETS_PATH + "/" + createdTweet.get("id").asString()
                + "/images/" + createdTweet.get("images").get(imageIndex).get("id").asString())
                .header(USER_ID_HEADER, UUID.randomUUID().toString());
    }

    private MockMultipartHttpServletRequestBuilder createRequest(UUID authorId) {
        return multipart(HttpMethod.POST, TWEETS_PATH).header(USER_ID_HEADER, authorId.toString());
    }

    private MockMultipartFile image(String filename, byte[] bytes) {
        return new MockMultipartFile("images", filename, MediaType.IMAGE_PNG_VALUE, bytes);
    }

    private JsonNode create(UUID authorId, String content, MockMultipartFile... images) throws Exception {
        MockMultipartHttpServletRequestBuilder request = createRequest(authorId).param("content", content);
        for (MockMultipartFile image : images) {
            request.file(image);
        }

        return json(mockMvc
                .perform(request)
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Tweet storedTweet(JsonNode responseBody) {
        return tweetRepository
                .findById(UUID.fromString(responseBody.get("id").asString()))
                .orElseThrow();
    }

    private List<Tweet> tweetsBy(UUID authorId) {
        return mongoTemplate.find(Query.query(Criteria.where("authorId").is(authorId)), Tweet.class);
    }

    private OutboxMessage outboxMessageFor(UUID tweetId) {
        return outboxMessagesFor(tweetId).get(0);
    }

    private List<OutboxMessage> outboxMessagesFor(UUID tweetId) {
        return outboxMessageRepository
                .findAll()
                .stream()
                .filter(message -> tweetId.toString().equals(message.getMessageKey()))
                .toList();
    }

    private List<String> imageIdsInEvent(Tweet tweet) throws Exception {
        JsonNode imageIds = objectMapper
                .readTree(outboxMessageFor(tweet.getId()).getPayload())
                .get("imageIds");

        return imageIds
                .valueStream()
                .map(JsonNode::asString)
                .toList();
    }

    private String contentTypeOf(String objectKey) throws Exception {
        return minioClient
                .statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build())
                .contentType();
    }

    private long objectCount() throws Exception {
        long count = 0;
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).build())) {
            result.get();
            count++;
        }

        return count;
    }
}
