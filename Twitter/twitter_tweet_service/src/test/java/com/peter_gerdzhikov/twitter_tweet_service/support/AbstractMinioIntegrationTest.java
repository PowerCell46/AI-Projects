package com.peter_gerdzhikov.twitter_tweet_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Adds a MinIO container next to the inherited Mongo and Kafka, started once per JVM. Every full-context
 * test extends this, because the bucket initializer needs a reachable MinIO on startup.
 *
 * <p>The container runs as root: the image's default {@code minio} user can't write {@code /data}.
 */
public abstract class AbstractMinioIntegrationTest extends AbstractKafkaIntegrationTest {

    protected static final String TEST_BUCKET = "tweet-images-test";

    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName
                    .parse("alpine/minio:RELEASE.2025-10-15T17-29-55Z")
                    .asCompatibleSubstituteFor("minio/minio")
    ).withCreateContainerCmdModifier(command -> command.withUser("root"));

    static {
        MINIO.start();
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("app.minio.url", MINIO::getS3URL);
        registry.add("app.minio.access-key", MINIO::getUserName);
        registry.add("app.minio.secret-key", MINIO::getPassword);
        registry.add("app.minio.bucket", () -> TEST_BUCKET);
    }
}
