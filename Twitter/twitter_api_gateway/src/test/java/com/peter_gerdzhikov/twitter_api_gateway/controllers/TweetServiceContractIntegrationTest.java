package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

/**
 * Runs the real tweet service, built from its Dockerfile, behind the gateway on a real server. The service
 * gets its own Mongo, MinIO and Kafka on a shared Docker network (reached by alias), because the containers
 * the other suites share are not on one. The gateway never sees a user row: authorization reads only the
 * token, so {@link TestJwts} stands in for the confirmation flow.
 *
 * <p>The first run builds the image, which downloads the service's dependencies and takes minutes; later
 * runs reuse Docker's layer cache.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // A real service doing Mongo and MinIO work needs more than the 1s the test profile allows a stub.
        properties = "spring.http.clients.read-timeout=10s"
)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TweetServiceContractIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int TWEET_SERVICE_PORT = 8081;

    private static final int KAFKA_INTERNAL_PORT = 19092;

    private static final String BOUNDARY = "contract-boundary";

    private static final String TWEETS_PATH = "/api/v1/tweets";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(60);

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final Network NETWORK = Network.newNetwork();

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.2")
            .withReplicaSet()
            .withNetwork(NETWORK)
            .withNetworkAliases("mongo");

    private static final MinIOContainer TWEET_MINIO = new MinIOContainer(
            DockerImageName
                    .parse("alpine/minio:RELEASE.2025-10-15T17-29-55Z")
                    .asCompatibleSubstituteFor("minio/minio")
    )
            .withCreateContainerCmdModifier(command -> command.withUser("root"))
            .withNetwork(NETWORK)
            .withNetworkAliases("minio");

    private static final KafkaContainer TWEET_KAFKA = new KafkaContainer("apache/kafka-native:4.3.1")
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withListener("kafka:" + KAFKA_INTERNAL_PORT);

    private static final GenericContainer<?> TWEET_SERVICE;

    static {
        MONGO.start();
        TWEET_MINIO.start();
        TWEET_KAFKA.start();

        TWEET_SERVICE = new GenericContainer<>(tweetServiceImage())
                .withNetwork(NETWORK)
                .withEnv("MONGODB_URI", "mongodb://mongo:27017/twitter_tweet_service_db?directConnection=true")
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:" + KAFKA_INTERNAL_PORT)
                .withEnv("MINIO_URL", "http://minio:9000")
                .withEnv("MINIO_ACCESS_KEY", TWEET_MINIO.getUserName())
                .withEnv("MINIO_SECRET_KEY", TWEET_MINIO.getPassword())
                .withExposedPorts(TWEET_SERVICE_PORT)
                .waitingFor(Wait
                        .forHttp("/actuator/health")
                        .forStatusCode(HttpStatus.OK.value())
                        .withStartupTimeout(Duration.ofMinutes(3)));
        TWEET_SERVICE.start();
    }

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private RestTestClient restTestClient;

    @DynamicPropertySource
    static void pointTheRoutesAtTheRealTweetService(DynamicPropertyRegistry registry) {
        registry.add("app.tweet-service.url",
                () -> "http://" + TWEET_SERVICE.getHost() + ":" + TWEET_SERVICE.getMappedPort(TWEET_SERVICE_PORT));
    }

    @Test
    void should_return_201_when_alice_posts_a_tweet_with_text_and_one_image() {
        UUID alice = UUID.randomUUID();

        JsonNode tweet = json(postTweet(alice, "hello", image(1024))
                .expectStatus().isCreated());

        assertThat(tweet.get("authorId").asString()).isEqualTo(alice.toString());
        assertThat(tweet.get("content").asString()).isEqualTo("hello");
        assertThat(tweet.get("images")).hasSize(1);
    }

    @Test
    void should_return_200_with_the_byte_identical_image_when_bob_reads_alices_tweet() {
        byte[] image = image(2048);
        JsonNode created = createTweet(UUID.randomUUID(), "look", image);
        UUID bob = UUID.randomUUID();

        JsonNode read = json(get(bob, TWEETS_PATH + "/" + created.get("id").asString())
                .expectStatus().isOk());
        byte[] loaded = get(bob, imagePath(created))
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(read.get("content").asString()).isEqualTo("look");
        assertThat(loaded).isEqualTo(image);
    }

    @Test
    void should_return_403_when_bob_edits_alices_tweet() {
        JsonNode created = createTweet(UUID.randomUUID(), "mine", image(1024));

        put(UUID.randomUUID(), tweetPath(created), "{\"content\":\"hijacked\"}")
                .expectStatus().isForbidden();
    }

    @Test
    void should_return_403_when_bob_deletes_alices_tweet() {
        JsonNode created = createTweet(UUID.randomUUID(), "mine", image(1024));

        delete(UUID.randomUUID(), tweetPath(created), null)
                .expectStatus().isForbidden();
    }

    @Test
    void should_return_403_when_bob_deletes_alices_tweet_with_a_spoofed_x_user_id() {
        UUID alice = UUID.randomUUID();
        JsonNode created = createTweet(alice, "mine", image(1024));

        delete(UUID.randomUUID(), tweetPath(created), alice.toString())
                .expectStatus().isForbidden();
    }

    @Test
    void should_return_200_when_alice_edits_her_tweet() {
        UUID alice = UUID.randomUUID();
        JsonNode created = createTweet(alice, "before", image(1024));

        JsonNode edited = json(put(alice, tweetPath(created), "{\"content\":\"after\"}")
                .expectStatus().isOk());

        assertThat(edited.get("content").asString()).isEqualTo("after");
    }

    @Test
    void should_return_204_then_404_on_read_when_alice_deletes_her_tweet() {
        UUID alice = UUID.randomUUID();
        JsonNode created = createTweet(alice, "short-lived", image(1024));

        delete(alice, tweetPath(created), null)
                .expectStatus().isNoContent();
        get(alice, tweetPath(created))
                .expectStatus().isNotFound();
    }

    @Test
    void should_publish_tweet_created_and_tweet_deleted_for_the_tweet_id_when_alice_creates_and_deletes_a_tweet() {
        UUID alice = UUID.randomUUID();
        String tweetId = createTweet(alice, "events", image(1024)).get("id").asString();
        delete(alice, TWEETS_PATH + "/" + tweetId, null)
                .expectStatus().isNoContent();

        JsonNode created = awaitEvent("tweet.created", tweetId);
        JsonNode deleted = awaitEvent("tweet.deleted", tweetId);

        assertThat(created.get("authorId").asString()).isEqualTo(alice.toString());
        assertThat(deleted.get("authorId").asString()).isEqualTo(alice.toString());
    }

    private static ImageFromDockerfile tweetServiceImage() {
        Path service = Path.of("..", "twitter_tweet_service");

        return new ImageFromDockerfile("twitter-tweet-service-contract", false)
                .withFileFromPath("Dockerfile", service.resolve("Dockerfile"))
                .withFileFromPath(".mvn", service.resolve(".mvn"))
                .withFileFromPath("mvnw", service.resolve("mvnw"))
                .withFileFromPath("pom.xml", service.resolve("pom.xml"))
                .withFileFromPath("src", service.resolve("src"));
    }

    private JsonNode createTweet(UUID author, String content, byte[] image) {
        return json(postTweet(author, content, image).expectStatus().isCreated());
    }

    private RestTestClient.ResponseSpec postTweet(UUID author, String content, byte[] image) {
        return restTestClient.post()
                .uri(TWEETS_PATH)
                .cookie(CookieFactory.COOKIE_NAME, cookieFor(author))
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                .body(multipartBody(content, image))
                .exchange();
    }

    private RestTestClient.ResponseSpec get(UUID caller, String path) {
        return restTestClient.get()
                .uri(path)
                .cookie(CookieFactory.COOKIE_NAME, cookieFor(caller))
                .exchange();
    }

    private RestTestClient.ResponseSpec put(UUID caller, String path, String jsonBody) {
        return restTestClient.put()
                .uri(path)
                .cookie(CookieFactory.COOKIE_NAME, cookieFor(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange();
    }

    private RestTestClient.ResponseSpec delete(UUID caller, String path, String spoofedUserId) {
        RestTestClient.RequestHeadersSpec<?> request = restTestClient.delete()
                .uri(path)
                .cookie(CookieFactory.COOKIE_NAME, cookieFor(caller));

        return spoofedUserId == null ? request.exchange() : request.header(USER_ID_HEADER, spoofedUserId).exchange();
    }

    private JsonNode json(RestTestClient.ResponseSpec response) {
        return jsonMapper.readTree(response
                .expectBody(String.class)
                .returnResult()
                .getResponseBody());
    }

    private String cookieFor(UUID userId) {
        return TestJwts.sign(TestJwts.validClaims().subject(userId.toString()), jwtSecret);
    }

    private String tweetPath(JsonNode tweet) {
        return TWEETS_PATH + "/" + tweet.get("id").asString();
    }

    private String imagePath(JsonNode tweet) {
        return tweetPath(tweet) + "/images/" + tweet.get("images").get(0).get("id").asString();
    }

    /**
     * Distinct bytes, not a run of one value, so a truncated or reordered download differs from the upload.
     */
    private byte[] image(int size) {
        byte[] bytes = Arrays.copyOf(PNG_SIGNATURE, size);
        for (int i = PNG_SIGNATURE.length; i < size; i++) {
            bytes[i] = (byte) i;
        }

        return bytes;
    }

    private byte[] multipartBody(String content, byte[] image) {
        byte[] contentPart = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"content\"\r\n\r\n"
                + content + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] imageHead = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"images\"; filename=\"photo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[contentPart.length + imageHead.length + image.length + tail.length];
        System.arraycopy(contentPart, 0, body, 0, contentPart.length);
        System.arraycopy(imageHead, 0, body, contentPart.length, imageHead.length);
        System.arraycopy(image, 0, body, contentPart.length + imageHead.length, image.length);
        System.arraycopy(tail, 0, body, body.length - tail.length, tail.length);

        return body;
    }

    /**
     * The real service polls its outbox every few seconds, so the event is awaited, not read once.
     */
    private JsonNode awaitEvent(String topic, String tweetId) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(java.util.Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TWEET_KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "contract-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));

            ConsumerRecord<String, String> record = Awaitility.await()
                    .atMost(EVENT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> firstRecordKeyedBy(consumer, tweetId), java.util.Objects::nonNull);

            return jsonMapper.readTree(record.value());
        }
    }

    private ConsumerRecord<String, String> firstRecordKeyedBy(KafkaConsumer<String, String> consumer, String key) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
            if (key.equals(record.key())) {
                return record;
            }
        }

        return null;
    }
}
