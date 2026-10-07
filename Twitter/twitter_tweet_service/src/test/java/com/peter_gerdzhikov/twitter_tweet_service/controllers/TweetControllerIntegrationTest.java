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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;
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

    private static final String INTERNAL_TWEETS_PATH = "/internal/v1/tweets";

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
    private ReplyRepository replyRepository;

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
            assertThat(body.has("views")).isFalse();
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
        void should_return_the_reply_count_when_the_tweet_has_replies() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "popular");
            UUID tweetId = UUID.fromString(created.get("id").asString());
            tweetRepository.incrementReplyCount(tweetId, 3);

            JsonNode body = getTweet(tweetId.toString(), authorId, status().isOk());
            JsonNode batch = findByIds(List.of(tweetId.toString()), status().isOk());

            assertThat(body.get("replyCount").asLong()).isEqualTo(3);
            assertThat(batch.get(0).get("replyCount").asLong()).isEqualTo(3);
        }

        @Test
        void should_return_a_reply_count_of_zero_when_the_tweet_has_none() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "quiet");

            JsonNode body = getTweet(created.get("id").asString(), authorId, status().isOk());

            assertThat(created.get("replyCount").asLong()).isZero();
            assertThat(body.get("replyCount").asLong()).isZero();
        }

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
        void should_leave_the_document_unchanged_and_return_no_views_when_the_tweet_is_read_repeatedly() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "read", image("a.png", TestImages.png()));
            String id = created.get("id").asString();
            Tweet before = storedTweet(created);

            getTweet(id, UUID.randomUUID(), status().isOk());
            getTweet(id, UUID.randomUUID(), status().isOk());
            JsonNode body = getTweet(id, UUID.randomUUID(), status().isOk());

            assertThat(body.has("views")).isFalse();
            assertThat(storedTweet(created)).usingRecursiveComparison().isEqualTo(before);
        }

        @Test
        void should_leave_updated_at_unchanged_when_the_tweet_is_read() throws Exception {
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
        void should_leave_the_document_unchanged_when_an_image_is_fetched() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "quiet", image("a.png", TestImages.png()));
            Tweet before = storedTweet(created);

            mockMvc
                    .perform(getImage(created, 0))
                    .andExpect(status().isOk());

            assertThat(storedTweet(created)).usingRecursiveComparison().isEqualTo(before);
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
            mutableClock.advance(Duration.ofHours(1));

            JsonNode body = update(id, authorId, "  after  ", status().isOk());

            assertThat(body.get("content").asString()).isEqualTo("after");
            assertThat(Instant.parse(body.get("updatedAt").asString()))
                    .isEqualTo(Instant.parse(body.get("createdAt").asString()).plus(Duration.ofHours(1)));
            assertThat(body.get("images")).isEqualTo(created.get("images"));
            assertThat(body.has("views")).isFalse();
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
        void should_remove_the_replies_of_the_tweet_and_keep_the_replies_of_others_when_the_tweet_is_deleted() throws Exception {
            UUID authorId = UUID.randomUUID();
            UUID tweetId = UUID.fromString(create(authorId, "bye").get("id").asString());
            Reply ofAnotherTweet = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));
            replyRepository.saveAll(List.of(TestDocuments.replyOn(tweetId), TestDocuments.replyOn(tweetId)));

            delete(tweetId.toString(), authorId, status().isNoContent());

            assertThat(replyRepository.findFirstPage(tweetId, 10)).isEmpty();
            assertThat(replyRepository.findById(ofAnotherTweet.getId())).isPresent();
        }

        @Test
        void should_answer_404_to_the_replies_list_when_the_tweet_is_deleted() throws Exception {
            UUID authorId = UUID.randomUUID();
            String id = create(authorId, "bye").get("id").asString();
            delete(id, authorId, status().isNoContent());

            mockMvc
                    .perform(get(TWEETS_PATH + "/" + id + "/replies").header(USER_ID_HEADER, authorId.toString()))
                    .andExpect(status().isNotFound());
        }

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

    @Nested
    class Internal {

        @Test
        void should_return_200_with_the_body_shape_when_the_tweet_exists() throws Exception {
            UUID authorId = UUID.randomUUID();
            JsonNode created = create(authorId, "internal", image("a.png", TestImages.png()));

            JsonNode body = findByIds(List.of(created.get("id").asString()), status().isOk());

            assertThat(body).hasSize(1);
            assertThat(body.get(0).get("id").asString()).isEqualTo(created.get("id").asString());
            assertThat(body.get(0).get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(body.get(0).get("content").asString()).isEqualTo("internal");
            assertThat(body.get(0).get("images")).hasSize(1);
            assertThat(body.get(0).get("images").get(0).has("objectKey")).isFalse();
        }

        @Test
        void should_return_only_the_tweets_that_exist_when_some_ids_are_unknown() throws Exception {
            String first = create(UUID.randomUUID(), "first").get("id").asString();
            String second = create(UUID.randomUUID(), "second").get("id").asString();

            JsonNode body = findByIds(List.of(first, UUID.randomUUID().toString(), second), status().isOk());

            assertThat(idsIn(body)).containsExactlyInAnyOrder(first, second);
        }

        @Test
        void should_return_an_empty_list_when_no_id_exists() throws Exception {
            JsonNode body = findByIds(List.of(UUID.randomUUID().toString()), status().isOk());

            assertThat(body).isEmpty();
        }

        @Test
        void should_return_each_tweet_once_when_an_id_is_repeated() throws Exception {
            String id = create(UUID.randomUUID(), "once").get("id").asString();

            JsonNode body = findByIds(List.of(id, id, id), status().isOk());

            assertThat(idsIn(body)).containsExactly(id);
        }

        @Test
        void should_accept_a_comma_separated_list_when_ids_are_joined() throws Exception {
            String first = create(UUID.randomUUID(), "first").get("id").asString();
            String second = create(UUID.randomUUID(), "second").get("id").asString();

            MvcResult result = mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH).param("ids", first + "," + second))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(idsIn(json(result))).containsExactlyInAnyOrder(first, second);
        }

        @Test
        void should_not_require_the_user_id_header() throws Exception {
            String id = create(UUID.randomUUID(), "anonymous").get("id").asString();

            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH).param("ids", id))
                    .andExpect(status().isOk());
        }

        @Test
        void should_return_no_views_and_leave_the_documents_unchanged_when_tweets_are_read() throws Exception {
            JsonNode created = create(UUID.randomUUID(), "unseen");
            String id = created.get("id").asString();
            Tweet before = storedTweet(created);

            JsonNode body = findByIds(List.of(id), status().isOk());
            findByIds(List.of(id), status().isOk());

            assertThat(body.get(0).has("views")).isFalse();
            assertThat(storedTweet(created)).usingRecursiveComparison().isEqualTo(before);
        }

        @Test
        void should_return_200_when_exactly_100_ids_are_given() throws Exception {
            findByIds(randomIds(100), status().isOk());
        }

        @Test
        void should_return_400_when_101_ids_are_given() throws Exception {
            JsonNode body = findByIds(randomIds(101), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Provide between 1 and 100 tweet ids.");
        }

        @Test
        void should_return_400_when_the_ids_parameter_is_empty() throws Exception {
            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH).param("ids", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_ids_parameter_is_missing() throws Exception {
            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_an_id_is_not_a_uuid() throws Exception {
            findByIds(List.of(UUID.randomUUID().toString(), "not-a-uuid"), status().isBadRequest());
        }
    }

    @Nested
    class InternalByAuthor {

        private static final Instant SINCE = Instant.parse("2026-03-01T00:00:00Z");

        @AfterEach
        void resetClock() {
            mutableClock.reset();
        }

        @Test
        void should_return_200_with_only_the_id_and_created_at_when_the_author_has_a_tweet() throws Exception {
            UUID authorId = UUID.randomUUID();
            mutableClock.setInstant(SINCE.plusSeconds(10));
            JsonNode created = create(authorId, "summary", image("a.png", TestImages.png()));

            JsonNode body = findByAuthor(authorId, SINCE.toString(), "50", status().isOk());

            assertThat(body).hasSize(1);
            assertThat(body.get(0).propertyNames()).containsExactlyInAnyOrder("id", "createdAt");
            assertThat(body.get(0).get("id").asString()).isEqualTo(created.get("id").asString());
            assertThat(Instant.parse(body.get(0).get("createdAt").asString())).isEqualTo(SINCE.plusSeconds(10));
        }

        @Test
        void should_return_the_tweets_newest_first_when_the_author_has_several() throws Exception {
            UUID authorId = UUID.randomUUID();
            String oldest = createAt(authorId, SINCE.plusSeconds(10));
            String newest = createAt(authorId, SINCE.plusSeconds(30));
            String middle = createAt(authorId, SINCE.plusSeconds(20));

            JsonNode body = findByAuthor(authorId, SINCE.toString(), "50", status().isOk());

            assertThat(idsIn(body)).containsExactly(newest, middle, oldest);
        }

        @Test
        void should_include_a_tweet_created_exactly_at_since_and_leave_out_an_older_one() throws Exception {
            UUID authorId = UUID.randomUUID();
            createAt(authorId, SINCE.minusMillis(1));
            String atSince = createAt(authorId, SINCE);

            JsonNode body = findByAuthor(authorId, SINCE.toString(), "50", status().isOk());

            assertThat(idsIn(body)).containsExactly(atSince);
        }

        @Test
        void should_return_only_the_newest_tweets_when_there_are_more_than_the_limit() throws Exception {
            UUID authorId = UUID.randomUUID();
            createAt(authorId, SINCE.plusSeconds(10));
            String newest = createAt(authorId, SINCE.plusSeconds(30));
            String middle = createAt(authorId, SINCE.plusSeconds(20));

            JsonNode body = findByAuthor(authorId, SINCE.toString(), "2", status().isOk());

            assertThat(idsIn(body)).containsExactly(newest, middle);
        }

        @Test
        void should_leave_out_the_tweets_of_other_authors() throws Exception {
            UUID authorId = UUID.randomUUID();
            String own = createAt(authorId, SINCE.plusSeconds(10));
            createAt(UUID.randomUUID(), SINCE.plusSeconds(20));

            JsonNode body = findByAuthor(authorId, SINCE.toString(), "50", status().isOk());

            assertThat(idsIn(body)).containsExactly(own);
        }

        @Test
        void should_return_an_empty_list_when_the_author_has_no_tweets() throws Exception {
            JsonNode body = findByAuthor(UUID.randomUUID(), SINCE.toString(), "50", status().isOk());

            assertThat(body).isEmpty();
        }

        @Test
        void should_not_require_the_user_id_header_and_leave_the_documents_unchanged() throws Exception {
            UUID authorId = UUID.randomUUID();
            mutableClock.setInstant(SINCE.plusSeconds(10));
            JsonNode created = create(authorId, "unchanged");
            Tweet before = storedTweet(created);

            findByAuthor(authorId, SINCE.toString(), "50", status().isOk());

            assertThat(storedTweet(created)).usingRecursiveComparison().isEqualTo(before);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "100"})
        void should_return_200_when_the_limit_is_at_the_edge_of_the_range(String limit) throws Exception {
            findByAuthor(UUID.randomUUID(), SINCE.toString(), limit, status().isOk());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "101"})
        void should_return_400_when_the_limit_is_outside_1_to_100(String limit) throws Exception {
            JsonNode body = findByAuthor(UUID.randomUUID(), SINCE.toString(), limit, status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Provide a limit between 1 and 100.");
        }

        @Test
        void should_return_400_when_the_limit_is_not_a_number() throws Exception {
            findByAuthor(UUID.randomUUID(), SINCE.toString(), "many", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_limit_is_missing() throws Exception {
            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH + "/by-author/" + UUID.randomUUID()).param("since", SINCE.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_since_is_missing() throws Exception {
            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH + "/by-author/" + UUID.randomUUID()).param("limit", "50"))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @ValueSource(strings = {"yesterday", "2026-03-01", "1772323200000", ""})
        void should_return_400_when_since_is_not_an_iso_8601_instant(String since) throws Exception {
            findByAuthor(UUID.randomUUID(), since, "50", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_author_id_is_not_a_uuid() throws Exception {
            mockMvc
                    .perform(get(INTERNAL_TWEETS_PATH + "/by-author/not-a-uuid")
                            .param("since", SINCE.toString())
                            .param("limit", "50"))
                    .andExpect(status().isBadRequest());
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

    private JsonNode findByIds(List<String> ids, ResultMatcher expected) throws Exception {
        return json(mockMvc
                .perform(get(INTERNAL_TWEETS_PATH).param("ids", ids.toArray(String[]::new)))
                .andExpect(expected)
                .andReturn());
    }

    private JsonNode findByAuthor(UUID authorId, String since, String limit, ResultMatcher expected)
            throws Exception {
        return json(mockMvc
                .perform(get(INTERNAL_TWEETS_PATH + "/by-author/" + authorId)
                        .param("since", since)
                        .param("limit", limit))
                .andExpect(expected)
                .andReturn());
    }

    private String createAt(UUID authorId, Instant createdAt) throws Exception {
        mutableClock.setInstant(createdAt);

        return create(authorId, "tweet").get("id").asString();
    }

    private List<String> idsIn(JsonNode tweets) {
        return tweets
                .valueStream()
                .map(tweet -> tweet.get("id").asString())
                .toList();
    }

    private List<String> randomIds(int count) {
        return Stream
                .generate(UUID::randomUUID)
                .map(UUID::toString)
                .limit(count)
                .toList();
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
