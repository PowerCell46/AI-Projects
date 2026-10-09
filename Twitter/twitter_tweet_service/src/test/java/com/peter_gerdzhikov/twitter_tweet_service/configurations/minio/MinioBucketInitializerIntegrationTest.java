package com.peter_gerdzhikov.twitter_tweet_service.configurations.minio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;

@SpringBootTest
@ActiveProfiles("test")
class MinioBucketInitializerIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioBucketInitializer minioBucketInitializer;

    @Nested
    class Startup {

        @Test
        void should_have_created_the_bucket_when_the_context_started() throws Exception {
            assertTrue(bucketExists(), "the bucket must exist right after startup");
        }
    }

    @Nested
    class Run {

        @Test
        void should_keep_the_bucket_and_not_fail_when_it_already_exists() throws Exception {
            assertDoesNotThrow(() -> minioBucketInitializer.run(new DefaultApplicationArguments()));

            assertTrue(bucketExists(), "the bucket must still exist after a second run");
        }

        @Test
        void should_recreate_the_bucket_when_it_is_missing() throws Exception {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(TEST_BUCKET).build());

            minioBucketInitializer.run(new DefaultApplicationArguments());

            assertTrue(bucketExists(), "the initializer must create a missing bucket");
        }
    }

    private boolean bucketExists() throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(TEST_BUCKET).build());
    }
}
